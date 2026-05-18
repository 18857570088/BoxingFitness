package com.zclei.boxingfitness

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale
import java.util.UUID

class BoxingBleManager(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onStateChanged(state: State)
        fun onDeviceListChanged(devices: List<DeviceCandidate>)
        fun onPacket(packet: BoxingPacket)
        fun onHit(packet: BoxingPacket)
        fun onPunchThresholdSensitivity(level: Int) = Unit
        fun onLog(message: String) = Unit
    }

    enum class ConnectionState {
        Idle,
        Scanning,
        DeviceFound,
        Connecting,
        Connected,
        ServicesReady,
        NotifyReady,
        Disconnected,
        Error,
    }

    data class State(
        val connectionState: ConnectionState = ConnectionState.Idle,
        val message: String = "未连接",
        val connectedDevice: DeviceCandidate? = null,
        val connectedDevices: List<DeviceCandidate> = emptyList(),
        val readyDevices: List<DeviceCandidate> = emptyList(),
    )

    enum class BoxingHand {
        Right,
        Left,
    }

    data class DeviceCandidate(
        val device: BluetoothDevice,
        val name: String,
        val address: String,
        val rssi: Int,
    ) {
        val hand: BoxingHand? = parseHand(name)
        val pairId: String? = parsePairId(name)
    }

    data class BoxingPacket(
        val rawHex: String,
        val frameSeq: Int,
        val powerState: Int,
        val punches: Int,
        val punchForce: Int = 0,
        val sample: BoxingSample,
        val deviceName: String? = null,
        val deviceAddress: String? = null,
        val hand: BoxingHand? = null,
        val pairId: String? = null,
        val hitDelta: Int = 1,
    )

    data class BoxingSample(
        val ax: Int,
        val ay: Int,
        val az: Int,
        val roll: Int,
        val pitch: Int,
        val yaw: Int,
        val pressure: Int,
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    private var scanning = false
    private val sessions = linkedMapOf<String, BleSession>()
    private var reconnectCandidates: List<DeviceCandidate> = emptyList()
    private var manualDisconnect = false
    private var reconnectAttempts = 0
    private var desiredGyroEnabled = false
    private var inactivityDisconnectRunnable: Runnable? = null
    private var debugWindowStartedAtMs = 0L
    private var debugPacketCount = 0
    private var debugHitEventCount = 0
    private var debugHitDeltaCount = 0
    private val candidates = linkedMapOf<String, DeviceCandidate>()
    private val seenScanAddresses = mutableSetOf<String>()
    private var state = State()
    private val deviceCandidateComparator =
        compareBy<DeviceCandidate>(
            { it.pairId?.toLongOrNull(radix = 36) ?: Long.MAX_VALUE },
            {
                when (it.hand) {
                    BoxingHand.Right -> 0
                    BoxingHand.Left -> 1
                    null -> 2
                }
            },
            { it.name },
            { it.address },
        )

    val devices: List<DeviceCandidate>
        get() = candidates.values.sortedWith(deviceCandidateComparator)

    val currentState: State
        get() = state

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            emitState(ConnectionState.Error, "手机蓝牙未开启")
            return
        }
        stopScan()
        if (sessions.isNotEmpty()) {
            disconnect(updateState = false)
            mainHandler.postDelayed({ startScanInternal(adapter) }, 600L)
            return
        }
        startScanInternal(adapter)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
    }

    @SuppressLint("MissingPermission")
    fun connect(candidate: DeviceCandidate) {
        stopScan()
        disconnect(updateState = false)
        manualDisconnect = false
        val pairCandidates = pairCandidatesFor(candidate)
        if (candidate.pairId != null && !isCompleteDistinctPair(pairCandidates)) {
            emitState(ConnectionState.Error, "未找到同编号完整左右手套")
            return
        }
        reconnectCandidates = pairCandidates
        reconnectAttempts = 0
        desiredGyroEnabled = false
        val expected = if (candidate.pairId != null) "同编号 ${candidate.pairId} 左右手套" else candidate.name
        emitState(ConnectionState.Connecting, "正在连接 $expected", candidate)
        connectCandidatesSequentially(pairCandidates)
    }

    @SuppressLint("MissingPermission")
    private fun connectCandidatesSequentially(pairCandidates: List<DeviceCandidate>) {
        pairCandidates.forEachIndexed { index, pairCandidate ->
            mainHandler.postDelayed({
                if (manualDisconnect || sessions.containsKey(pairCandidate.address)) {
                    return@postDelayed
                }
                val session = BleSession(pairCandidate)
                sessions[pairCandidate.address] = session
                log("准备连接 ${pairCandidate.name} ${pairCandidate.address}")
                session.gatt =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        pairCandidate.device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                    } else {
                        pairCandidate.device.connectGatt(appContext, false, gattCallback)
                    }
            }, index * PAIR_CONNECT_STAGGER_MS)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectFirst() {
        val first = candidates.values.firstOrNull()
        if (first == null) {
            emitState(ConnectionState.Error, "请先扫描 BOXING 设备")
        } else {
            connect(first)
        }
    }

    @SuppressLint("MissingPermission")
    fun enableGyro(): Boolean {
        sessions.values.forEach { it.lastPunchCount = null }
        desiredGyroEnabled = true
        return writeGyroCommand(enabled = true)
    }

    @SuppressLint("MissingPermission")
    fun disableGyro(): Boolean {
        sessions.values.forEach { it.lastPunchCount = null }
        desiredGyroEnabled = false
        return writeGyroCommand(enabled = false)
    }

    @SuppressLint("MissingPermission")
    fun readPunchThresholdSensitivity(): Boolean =
        writeCommand(BleCommand("读取拳击阈值", READ_PUNCH_THRESHOLD_COMMAND))

    @SuppressLint("MissingPermission")
    fun writePunchThresholdSensitivity(level: Int): Boolean {
        val value = level.coerceIn(0, 100).toByte()
        return writeCommand(
            BleCommand(
                label = "写入拳击阈值 ${level.coerceIn(0, 100)}",
                payload = byteArrayOf(0xC5.toByte(), 0x5C, PUNCH_THRESHOLD_COMMAND_ID.toByte(), 0x01, value),
            ),
        )
    }

    @SuppressLint("MissingPermission")
    fun disconnect(updateState: Boolean = true) {
        manualDisconnect = true
        reconnectAttempts = 0
        desiredGyroEnabled = false
        cancelInactivityDisconnectTimer()
        stopScan()
        sessions.values.toList().forEach { session ->
            session.pendingGyroState = null
            session.writeInFlight = false
            session.pendingCommands.clear()
            cancelGyroWriteRetry(session)
            cancelServiceDiscoveryTimeout(session)
            session.gatt?.let { gatt ->
                runCatching { writeGyroCommand(session, enabled = false) }
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
            }
        }
        sessions.clear()
        if (updateState) {
            emitState(ConnectionState.Disconnected, "已断开")
        }
    }

    fun release() {
        disconnect(updateState = false)
    }

    @SuppressLint("MissingPermission")
    private fun startScanInternal(adapter: BluetoothAdapter) {
        candidates.clear()
        seenScanAddresses.clear()
        emitDeviceList()
        scanning = true
        emitState(ConnectionState.Scanning, "正在扫描 BOXING 设备")
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        adapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        mainHandler.postDelayed({
            if (scanning) {
                stopScan()
                if (candidates.isEmpty()) {
                    emitState(ConnectionState.Error, "未发现 BOXING 设备")
                } else {
                    emitState(ConnectionState.DeviceFound, "扫描完成，发现 ${candidates.size} 个设备")
                    emitDeviceList()
                }
            }
        }, 10_000L)
    }

    private val scanCallback =
        object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                emitState(ConnectionState.Error, "扫描失败 code=$errorCode")
            }
        }

    @SuppressLint("MissingPermission")
    private fun handleScanResult(result: ScanResult) {
        val address = result.device.address.orEmpty()
        val names = scanNames(result)
        if (seenScanAddresses.add(address)) {
            log("扫描结果 name=${names.joinToString("|").ifBlank { "<empty>" }} address=$address rssi=${result.rssi}")
        }
        val name = names.firstOrNull { parseHand(it) != null && parsePairId(it) != null } ?: return
        val candidate =
            DeviceCandidate(
                device = result.device,
                name = name,
                address = address,
                rssi = result.rssi,
        )
        candidates[address] = candidate
        emitDeviceList()
        emitState(ConnectionState.DeviceFound, "发现 ${candidates.size} 个 BOXING 设备", candidate)
    }

    @SuppressLint("MissingPermission")
    private fun scanNames(result: ScanResult): List<String> =
        listOfNotNull(
            result.scanRecord?.deviceName,
            result.device.name,
            extractBoxingName(result.scanRecord?.bytes),
        ).map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    private fun extractBoxingName(bytes: ByteArray?): String? {
        if (bytes == null) return null
        val ascii =
            buildString(bytes.size) {
                bytes.forEach { byte ->
                    val value = byte.toInt() and 0xFF
                    append(if (value in 32..126) value.toChar() else ' ')
                }
            }
        val start = ascii.indexOf(TARGET_PREFIX, ignoreCase = true)
        if (start < 0) return null
        return ascii.substring(start).takeWhile { it.code in 33..126 }.ifBlank { null }
    }

    private val gattCallback =
        object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                val session = sessionFor(gatt)
                if (session == null) {
                    log("忽略未知 GATT 状态 status=$status state=$newState")
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    session.connected = true
                    reconnectAttempts = 0
                    session.writeInFlight = false
                    session.gyroWriteRetryCount = 0
                    emitState(ConnectionState.Connected, "${session.candidate.name} 已连接，正在发现服务")
                    mainHandler.postDelayed({
                        if (session.gatt === gatt) {
                            discoverServicesOnce(session)
                        }
                    }, SERVICE_DISCOVERY_START_DELAY_MS)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    runCatching { gatt.close() }
                    sessions.remove(session.candidate.address)
                    session.connected = false
                    session.gatt = null
                    session.servicesDiscoveryStarted = false
                    session.serviceDiscoveryRetryCount = 0
                    session.notifyReady = false
                    cancelServiceDiscoveryTimeout(session)
                    session.pendingReadCharacteristic = null
                    session.writeInFlight = false
                    session.pendingCommands.clear()
                    session.pendingGyroState = null
                    session.lastPunchCount = null
                    cancelGyroWriteRetry(session)
                    if (!manualDisconnect) {
                        sessions.values.toList().forEach { other ->
                            other.gatt?.let { otherGatt ->
                                runCatching { otherGatt.disconnect() }
                                runCatching { otherGatt.close() }
                            }
                            cancelServiceDiscoveryTimeout(other)
                            cancelGyroWriteRetry(other)
                        }
                        sessions.clear()
                    }
                    emitState(ConnectionState.Disconnected, "${session.candidate.name} 已断开 status=$status")
                    scheduleReconnectIfNeeded(status)
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val session = sessionFor(gatt)
                if (session == null) {
                    log("忽略旧 GATT 服务发现 status=$status")
                    return
                }
                cancelServiceDiscoveryTimeout(session)
                session.servicesDiscoveryStarted = false
                val service = gatt.getService(SERVICE_UUID)
                val notifyChar = service?.getCharacteristic(NOTIFY_UUID)
                val readChar = service?.getCharacteristic(READ_UUID)
                val writeChar = service?.getCharacteristic(WRITE_UUID)
                if (service == null || notifyChar == null || writeChar == null) {
                    emitState(ConnectionState.Error, "${session.candidate.name} 未找到 FFE0/FFE1/FFE4")
                    return
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    emitState(ConnectionState.Error, "${session.candidate.name} 服务发现失败 status=$status")
                    forceReconnect(session, "服务发现失败 status=$status")
                    return
                }
                session.pendingReadCharacteristic = readChar
                emitState(ConnectionState.ServicesReady, "${session.candidate.name} 服务已发现，正在打开通知")
                enableNotify(session, notifyChar)
            }

            @Deprecated("Deprecated in Java")
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                val session = sessionFor(gatt)
                if (session == null) {
                    log("忽略旧 GATT CCCD 回调 status=$status")
                    return
                }
                handleDescriptorWrite(session, status)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                val session = sessionFor(gatt)
                if (session == null) {
                    log("忽略旧 GATT 写入回调 status=$status")
                    return
                }
                session.writeInFlight = false
                log("${session.candidate.name} 写入 ${shortUuid(characteristic.uuid)} status=$status")
                flushPendingCommand(session)
                flushPendingGyroCommand(session)
                readPendingCharacteristic(session)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                val session = sessionFor(gatt) ?: return
                characteristic.value?.let { handleNotify(session, it) }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                val session = sessionFor(gatt) ?: return
                handleNotify(session, value)
            }
        }

    @SuppressLint("MissingPermission")
    private fun discoverServicesOnce(session: BleSession) {
        val gatt = session.gatt ?: return
        if (session.servicesDiscoveryStarted) return
        session.servicesDiscoveryStarted = true
        if (!gatt.discoverServices()) {
            session.servicesDiscoveryStarted = false
            emitState(ConnectionState.Error, "${session.candidate.name} 服务发现启动失败")
            forceReconnect(session, "服务发现启动失败")
        } else {
            scheduleServiceDiscoveryTimeout(session)
        }
    }

    @SuppressLint("MissingPermission")
    fun connectRememberedPair(
        leftAddress: String?,
        leftName: String?,
        rightAddress: String?,
        rightName: String?,
    ): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            emitState(ConnectionState.Error, "手机蓝牙未开启")
            return false
        }
        val remembered =
            listOfNotNull(
                rememberedCandidate(adapter, leftAddress, leftName),
                rememberedCandidate(adapter, rightAddress, rightName),
            )
        if (!isCompleteDistinctPair(remembered)) {
            emitState(ConnectionState.Error, "未保存完整左右手套蓝牙设备")
            return false
        }
        remembered.forEach { candidate -> candidates[candidate.address] = candidate }
        emitDeviceList()
        connect(remembered.firstOrNull { it.hand == BoxingHand.Right } ?: remembered.first())
        return true
    }

    private fun cancelGyroWriteRetry(session: BleSession) {
        session.gyroWriteRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        session.gyroWriteRetryRunnable = null
        session.gyroWriteRetryCount = 0
    }

    @SuppressLint("MissingPermission")
    private fun scheduleServiceDiscoveryTimeout(session: BleSession) {
        cancelServiceDiscoveryTimeout(session)
        val runnable =
            Runnable {
                if (sessions[session.candidate.address] !== session || !session.servicesDiscoveryStarted) {
                    return@Runnable
                }
                session.servicesDiscoveryStarted = false
                if (session.serviceDiscoveryRetryCount < MAX_SERVICE_DISCOVERY_RETRIES) {
                    session.serviceDiscoveryRetryCount += 1
                    log("${session.candidate.name} 服务发现超时，重试 ${session.serviceDiscoveryRetryCount}/$MAX_SERVICE_DISCOVERY_RETRIES")
                    discoverServicesOnce(session)
                } else {
                    emitState(ConnectionState.Error, "${session.candidate.name} 服务发现超时，准备重连")
                    forceReconnect(session, "服务发现超时")
                }
            }
        session.serviceDiscoveryTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, SERVICE_DISCOVERY_TIMEOUT_MS)
    }

    private fun cancelServiceDiscoveryTimeout(session: BleSession) {
        session.serviceDiscoveryTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        session.serviceDiscoveryTimeoutRunnable = null
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(
        session: BleSession,
        characteristic: BluetoothGattCharacteristic,
    ) {
        val gatt = session.gatt ?: return
        val localEnabled = gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (!localEnabled || descriptor == null) {
            emitState(ConnectionState.Error, "${session.candidate.name} 打开 FFE4 通知失败")
            return
        }
        val started =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        if (!started) {
            emitState(ConnectionState.Error, "${session.candidate.name} CCCD 写入启动失败")
        }
    }

    private fun handleDescriptorWrite(session: BleSession, status: Int) {
        log("${session.candidate.name} CCCD write status=$status")
        session.notifyReady = status == BluetoothGatt.GATT_SUCCESS
        emitState(
            ConnectionState.NotifyReady,
            if (desiredGyroEnabled) "${session.candidate.name} 通知已打开，恢复训练陀螺仪" else "${session.candidate.name} 通知已打开，陀螺仪保持关闭",
        )
        if (desiredGyroEnabled && !writeGyroCommand(session, enabled = true)) {
            readPendingCharacteristic(session)
        } else if (!desiredGyroEnabled) {
            readPendingCharacteristic(session)
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeGyroCommand(enabled: Boolean): Boolean {
        if (sessions.isEmpty()) return false
        var sent = false
        sessions.values.forEach { session ->
            sent = writeGyroCommand(session, enabled) || sent
        }
        return sent
    }

    @SuppressLint("MissingPermission")
    private fun writeGyroCommand(
        session: BleSession,
        enabled: Boolean,
    ): Boolean {
        if (session.gatt == null) return false
        if (session.writeInFlight) {
            session.pendingGyroState = enabled
            log("${session.candidate.name} ${if (enabled) "开启" else "关闭"}陀螺仪指令排队")
            return true
        }
        val command = if (enabled) GYRO_ON_COMMAND else GYRO_OFF_COMMAND
        val started = writeCommandNow(session, command, "${if (enabled) "开启" else "关闭"}陀螺仪")
        if (!started) {
            session.pendingGyroState = enabled
            scheduleGyroWriteRetry(session)
        }
        return started
    }

    @SuppressLint("MissingPermission")
    private fun writeCommand(command: BleCommand): Boolean {
        if (sessions.isEmpty()) return false
        var sent = false
        sessions.values.forEach { session ->
            sent = writeCommand(session, command) || sent
        }
        return sent
    }

    @SuppressLint("MissingPermission")
    private fun writeCommand(session: BleSession, command: BleCommand): Boolean {
        if (session.writeInFlight) {
            session.pendingCommands.addLast(command)
            log("${session.candidate.name} ${command.label}指令排队")
            return true
        }
        return writeCommandNow(session, command.payload, command.label)
    }

    @SuppressLint("MissingPermission")
    private fun writeCommandNow(
        session: BleSession,
        command: ByteArray,
        label: String,
    ): Boolean {
        val gatt = session.gatt ?: return false
        val char = gatt.getService(SERVICE_UUID)?.getCharacteristic(WRITE_UUID) ?: return false
        val started =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    char,
                    command,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = command
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        log("${session.candidate.name} $label 指令启动=$started ${command.toHex()}")
        session.writeInFlight = started
        return started
    }

    @SuppressLint("MissingPermission")
    private fun flushPendingCommand(session: BleSession) {
        if (session.writeInFlight || session.pendingCommands.isEmpty()) return
        val next = session.pendingCommands.removeFirst()
        writeCommandNow(session, next.payload, next.label)
    }

    @SuppressLint("MissingPermission")
    private fun flushPendingGyroCommand(session: BleSession) {
        val next = session.pendingGyroState ?: return
        session.pendingGyroState = null
        writeGyroCommand(session, next)
    }

    @SuppressLint("MissingPermission")
    private fun scheduleGyroWriteRetry(session: BleSession) {
        if (session.gyroWriteRetryRunnable != null || session.gyroWriteRetryCount >= MAX_GYRO_WRITE_RETRIES) {
            return
        }
        session.gyroWriteRetryCount += 1
        val runnable =
            Runnable {
                session.gyroWriteRetryRunnable = null
                if (sessions[session.candidate.address] !== session || session.writeInFlight || session.pendingGyroState == null) {
                    return@Runnable
                }
                log("${session.candidate.name} 重试陀螺仪指令 ${session.gyroWriteRetryCount}/$MAX_GYRO_WRITE_RETRIES")
                flushPendingGyroCommand(session)
            }
        session.gyroWriteRetryRunnable = runnable
        mainHandler.postDelayed(runnable, GYRO_WRITE_RETRY_DELAY_MS)
    }

    @SuppressLint("MissingPermission")
    private fun readPendingCharacteristic(session: BleSession) {
        val gatt = session.gatt ?: return
        if (session.writeInFlight) return
        val readChar = session.pendingReadCharacteristic ?: return
        session.pendingReadCharacteristic = null
        val started = gatt.readCharacteristic(readChar)
        log("${session.candidate.name} 读取 FFE1 启动=$started")
    }

    private fun handleNotify(session: BleSession, value: ByteArray) {
        synchronized(session.notifyBuffer) {
            value.forEach { session.notifyBuffer.addLast(it) }
            while (session.notifyBuffer.size >= 3) {
                while (session.notifyBuffer.size >= 2 && !(session.notifyBuffer[0] == 0xD5.toByte() && session.notifyBuffer[1] == 0x5D.toByte())) {
                    session.notifyBuffer.removeFirst()
                }
                if (session.notifyBuffer.size < 3) return
                val command = session.notifyBuffer[2].u()
                if (command == PUNCH_THRESHOLD_COMMAND_ID) {
                    if (session.notifyBuffer.size < PUNCH_THRESHOLD_RESPONSE_LEN) return
                    val frame = ByteArray(PUNCH_THRESHOLD_RESPONSE_LEN) { session.notifyBuffer.removeFirst() }
                    val level = frame[3].u().coerceIn(0, 100)
                    log("${session.candidate.name} 读取拳击阈值返回 sensitivity=$level threshold=${sensitivityToThreshold(level)}")
                    emitPunchThresholdSensitivity(level)
                    continue
                }
                if (command != PACKET_COMMAND_ID) {
                    session.notifyBuffer.removeFirst()
                    continue
                }
                if (session.notifyBuffer.size < FRAME_LEN) return
                val frame = ByteArray(FRAME_LEN) { session.notifyBuffer.removeFirst() }
                parsePacket(session, frame)?.let { packet ->
                    emitPacket(packet)
                    detectHit(session, packet)
                }
            }
        }
    }

    private fun parsePacket(session: BleSession, frame: ByteArray): BoxingPacket? {
        if (frame.size != FRAME_LEN ||
            frame[0] != 0xD5.toByte() ||
            frame[1] != 0x5D.toByte() ||
            frame[2] != PACKET_COMMAND_ID.toByte()
        ) {
            return null
        }
        return BoxingPacket(
            rawHex = frame.toHex(),
            frameSeq = frame[3].u(),
            powerState = frame[4].u(),
            punches = frame[5].u(),
            punchForce = frame[7].u(),
            sample =
                BoxingSample(
                    ax = 0,
                    ay = 0,
                    az = 0,
                    roll = 0,
                    pitch = 0,
                    yaw = 0,
                    pressure = 0,
                ),
            deviceName = session.candidate.name,
            deviceAddress = session.candidate.address,
            hand = session.candidate.hand,
            pairId = session.candidate.pairId,
        )
    }

    private fun detectHit(session: BleSession, packet: BoxingPacket) {
        recordPacketDetail(session, packet)
        val previous = session.lastPunchCount
        session.lastPunchCount = packet.punches
        if (previous == null) {
            recordHitDetail(session, packet, delta = 0)
            return
        }
        val delta = (packet.punches - previous + PUNCH_COUNTER_MODULO) % PUNCH_COUNTER_MODULO
        recordHitDetail(session, packet, delta)
        if (delta <= 0 || delta > MAX_PUNCH_DELTA_PER_PACKET) {
            if (delta > MAX_PUNCH_DELTA_PER_PACKET) {
                Log.d(
                    DEBUG_DETAIL_TAG,
                    "${session.candidate.name} drop delta=$delta previous=$previous current=${packet.punches} raw=${packet.rawHex}",
                )
            }
            return
        }
        emitHit(packet.copy(hitDelta = delta))
    }

    private fun recordPacketDetail(
        session: BleSession,
        packet: BoxingPacket,
    ) {
        val now = SystemClock.elapsedRealtime()
        val gap = if (session.lastPacketAtMs > 0L) now - session.lastPacketAtMs else 0L
        session.lastPacketAtMs = now
        if (session.debugDetailWindowStartedAtMs == 0L) {
            session.debugDetailWindowStartedAtMs = now
        }
        session.debugDetailPackets += 1
        if (gap > session.debugDetailMaxPacketGapMs) {
            session.debugDetailMaxPacketGapMs = gap
        }
        session.debugDetailLastPunches = packet.punches
    }

    private fun recordHitDetail(
        session: BleSession,
        packet: BoxingPacket,
        delta: Int,
    ) {
        if (delta <= 0) {
            session.debugDetailZeroDeltaPackets += 1
        } else {
            session.debugDetailHits += delta
            if (delta > session.debugDetailMaxHitDelta) {
                session.debugDetailMaxHitDelta = delta
            }
        }
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - session.debugDetailWindowStartedAtMs
        if (elapsed < 1_000L) return
        Log.d(
            DEBUG_DETAIL_TAG,
            "${session.candidate.name} hand=${packet.hand} packets=${session.debugDetailPackets} " +
                "zeroDelta=${session.debugDetailZeroDeltaPackets} hits=${session.debugDetailHits} " +
                "maxGap=${session.debugDetailMaxPacketGapMs}ms maxDelta=${session.debugDetailMaxHitDelta} " +
                "lastPunches=${session.debugDetailLastPunches} force=${packet.punchForce} elapsed=${elapsed}ms",
        )
        session.debugDetailWindowStartedAtMs = now
        session.debugDetailPackets = 0
        session.debugDetailZeroDeltaPackets = 0
        session.debugDetailHits = 0
        session.debugDetailMaxPacketGapMs = 0L
        session.debugDetailMaxHitDelta = 0
    }

    private fun emitState(
        connectionState: ConnectionState,
        message: String,
        device: DeviceCandidate? = null,
    ) {
        val connectedDevices = sessions.values.filter { it.connected }.map { it.candidate }.sortedWith(deviceCandidateComparator)
        val readyDevices = sessions.values.filter { it.connected && it.notifyReady }.map { it.candidate }.sortedWith(deviceCandidateComparator)
        state =
            State(
                connectionState = connectionState,
                message = message,
                connectedDevice = device ?: connectedDevices.firstOrNull(),
                connectedDevices = connectedDevices,
                readyDevices = readyDevices,
        )
        log(message)
        if (connectedDevices.isNotEmpty()) {
            scheduleInactivityDisconnectIfNeeded()
        } else {
            cancelInactivityDisconnectTimer()
        }
        mainHandler.post { listener.onStateChanged(state) }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
        mainHandler.post { listener.onLog(message) }
    }

    private fun emitDeviceList() {
        val snapshot = devices
        mainHandler.post { listener.onDeviceListChanged(snapshot) }
    }

    private fun emitPacket(packet: BoxingPacket) {
        recordDebugStats(packet = packet, hitDelta = 0)
        mainHandler.post { listener.onPacket(packet) }
    }

    private fun emitHit(packet: BoxingPacket) {
        resetInactivityDisconnectTimer()
        recordDebugStats(packet = packet, hitDelta = packet.hitDelta)
        mainHandler.post { listener.onHit(packet) }
    }

    private fun recordDebugStats(
        packet: BoxingPacket,
        hitDelta: Int,
    ) {
        if (hitDelta > 0) {
            debugHitEventCount += 1
            debugHitDeltaCount += hitDelta
        } else {
            debugPacketCount += 1
        }
        val now = SystemClock.elapsedRealtime()
        if (debugWindowStartedAtMs == 0L) {
            debugWindowStartedAtMs = now
            return
        }
        val elapsed = now - debugWindowStartedAtMs
        if (elapsed < 1_000L) return
        Log.d(
            DEBUG_STATS_TAG,
            "packets=${debugPacketCount} hitEvents=${debugHitEventCount} hitDelta=${debugHitDeltaCount} " +
                "elapsed=${elapsed}ms hand=${packet.hand} pair=${packet.pairId} punches=${packet.punches}",
        )
        debugWindowStartedAtMs = now
        debugPacketCount = 0
        debugHitEventCount = 0
        debugHitDeltaCount = 0
    }

    private fun scheduleInactivityDisconnectIfNeeded() {
        if (sessions.isEmpty() || inactivityDisconnectRunnable != null) return
        val runnable =
            Runnable {
                inactivityDisconnectRunnable = null
                if (sessions.isNotEmpty()) {
                    disconnectForInactivity()
                }
            }
        inactivityDisconnectRunnable = runnable
        mainHandler.postDelayed(runnable, BLE_INACTIVITY_DISCONNECT_TIMEOUT_MS)
    }

    private fun resetInactivityDisconnectTimer() {
        cancelInactivityDisconnectTimer()
        scheduleInactivityDisconnectIfNeeded()
    }

    private fun cancelInactivityDisconnectTimer() {
        inactivityDisconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        inactivityDisconnectRunnable = null
    }

    @SuppressLint("MissingPermission")
    private fun disconnectForInactivity() {
        if (sessions.isEmpty()) return
        disconnect(updateState = false)
        emitState(ConnectionState.Disconnected, "10分钟未检测到拳击动作，已断开蓝牙")
    }

    private fun emitPunchThresholdSensitivity(level: Int) {
        mainHandler.post { listener.onPunchThresholdSensitivity(level) }
    }

    @SuppressLint("MissingPermission")
    private fun forceReconnect(session: BleSession, reason: String) {
        val gatt = session.gatt
        log("${session.candidate.name} $reason，关闭当前 GATT")
        sessions.remove(session.candidate.address)
        session.gatt = null
        session.servicesDiscoveryStarted = false
        session.notifyReady = false
        cancelServiceDiscoveryTimeout(session)
        session.pendingReadCharacteristic = null
        session.writeInFlight = false
        session.pendingCommands.clear()
        session.pendingGyroState = null
        cancelGyroWriteRetry(session)
        session.lastPunchCount = null
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        scheduleReconnectIfNeeded(GATT_INTERNAL_RECONNECT_STATUS)
    }

    @SuppressLint("MissingPermission")
    private fun scheduleReconnectIfNeeded(status: Int) {
        val candidatesToReconnect = reconnectCandidates.ifEmpty { state.connectedDevices.ifEmpty { state.connectedDevice?.let { listOf(it) } ?: emptyList() } }
        if (candidatesToReconnect.isEmpty()) return
        if (manualDisconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            log("跳过自动重连 manual=$manualDisconnect status=$status attempts=$reconnectAttempts")
            return
        }
        reconnectAttempts += 1
        val attempt = reconnectAttempts
        emitState(ConnectionState.Connecting, "蓝牙断开，正在自动重连 $attempt/$MAX_RECONNECT_ATTEMPTS", candidatesToReconnect.firstOrNull())
        mainHandler.postDelayed({
            if (manualDisconnect || sessions.isNotEmpty()) {
                return@postDelayed
            }
            connectCandidatesSequentially(candidatesToReconnect)
        }, RECONNECT_DELAY_MS)
    }

    private fun sessionFor(gatt: BluetoothGatt): BleSession? =
        sessions.values.firstOrNull { it.gatt === gatt }

    private fun pairCandidatesFor(candidate: DeviceCandidate): List<DeviceCandidate> {
        val pairId = candidate.pairId
        val pair =
            if (pairId == null) {
                listOf(candidate)
            } else {
                devices.filter { it.pairId == pairId }
            }
        return pair.ifEmpty { listOf(candidate) }.sortedWith(deviceCandidateComparator)
    }

    private fun isCompleteDistinctPair(candidates: List<DeviceCandidate>): Boolean {
        val left = candidates.firstOrNull { it.hand == BoxingHand.Left }
        val right = candidates.firstOrNull { it.hand == BoxingHand.Right }
        if (left == null || right == null) return false
        if (left.address.equals(right.address, ignoreCase = true)) return false
        val leftPairId = left.pairId
        val rightPairId = right.pairId
        return leftPairId != null && leftPairId == rightPairId
    }

    private fun rememberedCandidate(
        adapter: BluetoothAdapter,
        address: String?,
        name: String?,
    ): DeviceCandidate? {
        val normalizedAddress = address?.trim().orEmpty()
        val normalizedName = name?.trim().orEmpty()
        if (normalizedAddress.isBlank() || normalizedName.isBlank()) return null
        val device =
            runCatching { adapter.getRemoteDevice(normalizedAddress) }
                .getOrNull() ?: return null
        return DeviceCandidate(
            device = device,
            name = normalizedName,
            address = normalizedAddress,
            rssi = 0,
        )
    }

    private data class BleSession(
        val candidate: DeviceCandidate,
        var gatt: BluetoothGatt? = null,
        var connected: Boolean = false,
        var pendingReadCharacteristic: BluetoothGattCharacteristic? = null,
        var servicesDiscoveryStarted: Boolean = false,
        var notifyReady: Boolean = false,
        var lastPunchCount: Int? = null,
        var writeInFlight: Boolean = false,
        val pendingCommands: ArrayDeque<BleCommand> = ArrayDeque(),
        var pendingGyroState: Boolean? = null,
        var gyroWriteRetryRunnable: Runnable? = null,
        var gyroWriteRetryCount: Int = 0,
        var serviceDiscoveryRetryCount: Int = 0,
        var serviceDiscoveryTimeoutRunnable: Runnable? = null,
        val notifyBuffer: ArrayDeque<Byte> = ArrayDeque(),
        var lastPacketAtMs: Long = 0L,
        var debugDetailWindowStartedAtMs: Long = 0L,
        var debugDetailPackets: Int = 0,
        var debugDetailZeroDeltaPackets: Int = 0,
        var debugDetailHits: Int = 0,
        var debugDetailMaxPacketGapMs: Long = 0L,
        var debugDetailMaxHitDelta: Int = 0,
        var debugDetailLastPunches: Int = 0,
    )

    private fun Byte.u(): Int = toInt() and 0xFF

    private fun ByteArray.toHex(): String =
        joinToString(" ") { "%02X".format(Locale.US, it.toInt() and 0xFF) }

    private fun shortUuid(uuid: UUID): String = uuid.toString().substring(4, 8).uppercase(Locale.US)

    private fun sensitivityToThreshold(level: Int): Int = 6000 - level.coerceIn(0, 100) * 40

    private data class BleCommand(
        val label: String,
        val payload: ByteArray,
    )

    private companion object {
        const val TAG = "BoxingBleManager"
        const val TARGET_PREFIX = "BOXING"
        const val FRAME_LEN = 11
        const val PACKET_COMMAND_ID = 0x03
        const val PUNCH_THRESHOLD_COMMAND_ID = 0x05
        const val PUNCH_THRESHOLD_RESPONSE_LEN = 4
        const val PUNCH_COUNTER_MODULO = 256
        const val MAX_PUNCH_DELTA_PER_PACKET = 12
        const val DEBUG_STATS_TAG = "BoxingBleStats"
        const val DEBUG_DETAIL_TAG = "BoxingBleDetail"
        val SERVICE_UUID: UUID = uuid16("FFE0")
        val READ_UUID: UUID = uuid16("FFE1")
        val WRITE_UUID: UUID = uuid16("FFE1")
        val NOTIFY_UUID: UUID = uuid16("FFE4")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val GYRO_ON_COMMAND = byteArrayOf(0xC5.toByte(), 0x5C, 0x04, 0x01)
        val GYRO_OFF_COMMAND = byteArrayOf(0xC5.toByte(), 0x5C, 0x04, 0x00)
        val READ_PUNCH_THRESHOLD_COMMAND = byteArrayOf(0xC5.toByte(), 0x5C, PUNCH_THRESHOLD_COMMAND_ID.toByte(), 0x00)
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val RECONNECT_DELAY_MS = 900L
        const val PAIR_CONNECT_STAGGER_MS = 1_200L
        const val MAX_GYRO_WRITE_RETRIES = 5
        const val GYRO_WRITE_RETRY_DELAY_MS = 220L
        const val SERVICE_DISCOVERY_START_DELAY_MS = 650L
        const val MAX_SERVICE_DISCOVERY_RETRIES = 2
        const val SERVICE_DISCOVERY_TIMEOUT_MS = 4_000L
        const val GATT_INTERNAL_RECONNECT_STATUS = 133
        const val BLE_INACTIVITY_DISCONNECT_TIMEOUT_MS = 10 * 60 * 1_000L
        val DEVICE_NAME_REGEX = Regex("^BOXING#([RL])([0-9A-Z]{6})$", RegexOption.IGNORE_CASE)

        fun uuid16(value: String): UUID =
            UUID.fromString("0000${value.lowercase(Locale.US)}-0000-1000-8000-00805f9b34fb")

        fun parseHand(name: String): BoxingHand? =
            when (DEVICE_NAME_REGEX.matchEntire(name.trim())?.groupValues?.getOrNull(1)?.uppercase(Locale.US)) {
                "R" -> BoxingHand.Right
                "L" -> BoxingHand.Left
                else -> null
            }

        fun parsePairId(name: String): String? =
            DEVICE_NAME_REGEX.matchEntire(name.trim())?.groupValues?.getOrNull(2)?.uppercase(Locale.US)
    }
}

