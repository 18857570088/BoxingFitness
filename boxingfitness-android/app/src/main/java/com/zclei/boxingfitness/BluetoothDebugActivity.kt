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
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class BluetoothDebugActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val targetPrefix = "BOXING"
    private val serviceUuid = uuid16("FFE0")
    private val readUuid = uuid16("FFE1")
    private val writeUuid = uuid16("FFE1")
    private val notifyUuid = uuid16("FFE4")
    private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanning = false
    private var selectedDevice: BluetoothDevice? = null
    private var servicesDiscoveryStarted = false
    private var serviceDiscoveryRetryCount = 0
    private var serviceDiscoveryTimeoutRunnable: Runnable? = null
    private var reconnectAttempts = 0
    private var manualDisconnect = false
    private var reconnectDevice: BluetoothDevice? = null
    private var pendingReadCharacteristic: BluetoothGattCharacteristic? = null
    private var lastPunchCount: Int? = null
    private var lastPacketLogAtMs = 0L
    private var lastHitFlashAtMs = 0L
    private var notifyDebugCount = 0
    private val notifyBuffer = ArrayDeque<Byte>()
    private val candidates = linkedMapOf<String, DeviceCandidate>()
    private val debugSeenScanAddresses = mutableSetOf<String>()

    private lateinit var statusView: TextView
    private lateinit var deviceView: TextView
    private lateinit var scanButton: Button
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var deviceListContainer: LinearLayout
    private lateinit var logView: TextView
    private lateinit var batteryValue: TextView
    private lateinit var chargeValue: TextView
    private lateinit var pressureValue: TextView
    private lateinit var punchValue: TextView
    private lateinit var hitButton: Button
    private lateinit var gyroOnButton: Button
    private lateinit var gyroOffButton: Button
    private lateinit var accelValue: TextView
    private lateinit var angleValue: TextView
    private lateinit var rawValue: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasBluetoothPermissions()) {
                startScan()
            } else {
                setStatus("缺少蓝牙权限，无法扫描设备", "#FFAA40")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter
        setContentView(buildContentView())
        setStatus("未连接，点击扫描查找 BOXING 设备", "#FFD060")
        updateButtons()
    }

    override fun onDestroy() {
        stopScan()
        closeGatt()
        super.onDestroy()
    }

    private fun buildContentView(): View {
        val root =
            ScrollView(this).apply {
                setBackgroundColor(Color.parseColor("#07111A"))
                isFillViewport = true
            }
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(24))
            }
        root.addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        content.addView(title("蓝牙数据调试", 28f))
        content.addView(
            body("只显示名称以 BOXING 开头的设备    数据包：20字节    服务：FFE0    写入：FFE1    通知：FFE4").apply {
                setTextColor(Color.parseColor("#9FB4C6"))
                setPadding(0, dp(6), 0, dp(14))
            },
        )

        statusView = chip("准备扫描")
        content.addView(statusView)

        deviceView =
            body("未选择设备").apply {
                setPadding(0, dp(14), 0, dp(8))
                setTextColor(Color.parseColor("#D6E9F8"))
            }
        content.addView(deviceView)

        val controls =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(14))
            }
        scanButton = actionButton("扫描")
        connectButton = actionButton("连接")
        disconnectButton = actionButton("断开")
        controls.addView(scanButton, weightedParams(1f, right = 8))
        controls.addView(connectButton, weightedParams(1f, right = 8))
        controls.addView(disconnectButton, weightedParams(1f))
        content.addView(controls)

        scanButton.setOnClickListener {
            if (hasBluetoothPermissions()) {
                startScan()
            } else {
                permissionLauncher.launch(requiredPermissions())
            }
        }
        connectButton.setOnClickListener {
            val device = selectedDevice
            if (device == null) {
                setStatus("请先在列表中选择 BOXING 设备", "#FFAA40")
            } else {
                connect(device)
            }
        }
        disconnectButton.setOnClickListener { closeGatt() }

        val listCard = card()
        listCard.addView(section("扫描列表"))
        deviceListContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
        deviceListContainer.addView(
            body("暂无设备").apply {
                setTextColor(Color.parseColor("#8FA7B8"))
            },
        )
        listCard.addView(deviceListContainer)
        content.addView(listCard)

        val metrics = card()
        metrics.addView(section("实时参数"))
        val row1 = metricRow()
        batteryValue = metric("电量", "--")
        chargeValue = metric("充电", "--")
        row1.addView(batteryValue, weightedParams(1f, right = 8))
        row1.addView(chargeValue, weightedParams(1f))
        metrics.addView(row1)

        val row2 = metricRow()
        pressureValue = metric("拳击力度", "--")
        punchValue = metric("拳击次数", "--")
        row2.addView(pressureValue, weightedParams(1f, right = 8))
        row2.addView(punchValue, weightedParams(1f))
        metrics.addView(row2)

        val hitRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(8))
            }
        hitButton =
            actionButton("击中").apply {
                isClickable = false
                minHeight = dp(96)
                minimumHeight = dp(96)
                background = rounded("#182B35", "#31596A", 18)
                textSize = 22f
            }
        hitRow.addView(
            hitButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        metrics.addView(hitRow)

        val gyroRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(8))
            }
        gyroOnButton = actionButton("开启")
        gyroOffButton = actionButton("关闭")
        gyroRow.addView(gyroOnButton, weightedParams(1f, right = 8))
        gyroRow.addView(gyroOffButton, weightedParams(1f))
        metrics.addView(gyroRow)
        gyroOnButton.setOnClickListener { sendGyroCommandFromUi(enabled = true) }
        gyroOffButton.setOnClickListener { sendGyroCommandFromUi(enabled = false) }

        accelValue = wideMetric("有效数据", "数据0 包数 --    数据1 电量 --    数据2 次数 --    数据4 力度 --")
        angleValue = wideMetric("忽略数据", "数据3 / 数据5 / 数据6 / 数据7 已忽略")
        rawValue = wideMetric("原始帧", "--")
        metrics.addView(accelValue)
        metrics.addView(angleValue)
        metrics.addView(rawValue)
        content.addView(metrics)

        val logCard = card()
        logCard.addView(section("连接日志"))
        logView =
            body("").apply {
                setTextColor(Color.parseColor("#CBE2F5"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.MONOSPACE
            }
        logCard.addView(logView)
        content.addView(logCard)

        return root
    }

    private fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun hasBluetoothPermissions(): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            setStatus("手机蓝牙未开启", "#FFAA40")
            return
        }
        stopScan()
        if (bluetoothGatt != null) {
            appendLog("扫描前先断开当前 GATT，等待设备重新广播")
            closeGatt(updateStatus = false)
            setStatus("正在断开旧连接，准备扫描", "#FFD060")
            mainHandler.postDelayed({ startScanAfterDisconnect(adapter) }, 600L)
            return
        }
        startScanAfterDisconnect(adapter)
    }

    @SuppressLint("MissingPermission")
    private fun startScanAfterDisconnect(adapter: BluetoothAdapter) {
        selectedDevice = null
        candidates.clear()
        debugSeenScanAddresses.clear()
        renderDeviceList()
        scanning = true
        updateButtons()
        setStatus("正在扫描 BOXING 设备", "#40AAFF")
        appendLog("开始扫描，仅显示名称以 $targetPrefix 开头的设备")
        val settings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
        adapter.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        mainHandler.postDelayed({
            if (scanning) {
                stopScan()
                renderDeviceList()
                if (candidates.isEmpty()) {
                    setStatus("未发现 BOXING 设备，请靠近后重试", "#FFAA40")
                    appendLog("扫描结束，候选设备数量=0")
                } else {
                    ensureDefaultCandidateSelected()
                    setStatus("扫描完成，列表中有 ${candidates.size} 个设备，请选择连接", "#FFD060")
                    appendLog("扫描结束，候选设备数量=${candidates.size}")
                }
            }
        }, 10_000L)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        scanning = false
        updateButtons()
    }

    private val scanCallback =
        object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address.orEmpty()
                val serviceText =
                    result.scanRecord
                        ?.serviceUuids
                        ?.joinToString(",") { it.uuid.toString().substring(4, 8).uppercase(Locale.US) }
                        .orEmpty()
                val candidate = buildDeviceCandidate(result)
                runOnUiThread {
                    if (debugSeenScanAddresses.add(address)) {
                        appendLog(
                            "扫描结果 name=${scanNames(result).joinToString("|").ifBlank { "<empty>" }} " +
                                "address=$address rssi=${result.rssi} services=$serviceText",
                        )
                    }
                    if (candidate == null) return@runOnUiThread
                    candidates[address] = candidate
                    if (selectedDevice == null) {
                        selectedDevice = candidate.device
                        deviceView.text = "${candidate.name}\n${candidate.address}    RSSI ${candidate.rssi} dBm"
                    }
                    renderDeviceList()
                    updateButtons()
                    setStatus("发现 ${candidates.size} 个 BOXING 设备", "#FFD060")
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                runOnUiThread {
                    setStatus("扫描失败：$errorCode", "#FFAA40")
                    appendLog("扫描失败 code=$errorCode")
                    updateButtons()
                }
            }
        }

    @SuppressLint("MissingPermission")
    private fun buildDeviceCandidate(result: ScanResult): DeviceCandidate? {
        val address = result.device.address.orEmpty()
        val name =
            scanNames(result)
                .firstOrNull { it.startsWith(targetPrefix, ignoreCase = true) }
                ?: return null
        return DeviceCandidate(
            device = result.device,
            name = name,
            address = address,
            rssi = result.rssi,
        )
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
        val start = ascii.indexOf(targetPrefix, ignoreCase = true)
        if (start < 0) return null
        return ascii.substring(start).takeWhile { it.code in 33..126 }.ifBlank { null }
    }

    private fun ensureDefaultCandidateSelected() {
        if (selectedDevice != null) return
        val candidate = candidates.values.firstOrNull() ?: return
        selectedDevice = candidate.device
        deviceView.text = "${candidate.name}\n${candidate.address}    RSSI ${candidate.rssi} dBm"
        updateButtons()
    }

    private fun renderDeviceList() {
        if (!::deviceListContainer.isInitialized) return
        deviceListContainer.removeAllViews()
        if (candidates.isEmpty()) {
            deviceListContainer.addView(
                body("暂无设备").apply {
                    setTextColor(Color.parseColor("#8FA7B8"))
                },
            )
            return
        }
        appendLog("刷新扫描列表 count=${candidates.size}")
        candidates.values.forEach { candidate ->
            val selected = candidate.device.address == selectedDevice?.address
            deviceListContainer.addView(
                body("${candidate.name}\n${candidate.address}    RSSI ${candidate.rssi} dBm").apply {
                    setTextColor(if (selected) Color.WHITE else Color.parseColor("#D7E8F4"))
                    setTypeface(Typeface.DEFAULT_BOLD)
                    background =
                        rounded(
                            if (selected) "#275E72" else "#0A1722",
                            if (selected) "#69D2EF" else "#1D3A4C",
                            14,
                        )
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { bottomMargin = dp(8) }
                    setOnClickListener {
                        selectedDevice = candidate.device
                        deviceView.text = "${candidate.name}\n${candidate.address}    RSSI ${candidate.rssi} dBm"
                        setStatus("已选择 ${candidate.name}，点击连接", "#FFD060")
                        appendLog("选择 ${candidate.name} ${candidate.address}")
                        renderDeviceList()
                        updateButtons()
                    }
                },
            )
        }
        deviceListContainer.requestLayout()
        deviceListContainer.invalidate()
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        stopScan()
        closeGatt(updateStatus = false)
        manualDisconnect = false
        reconnectDevice = device
        reconnectAttempts = 0
        notifyBuffer.clear()
        notifyDebugCount = 0
        servicesDiscoveryStarted = false
        serviceDiscoveryRetryCount = 0
        cancelServiceDiscoveryTimeout()
        pendingReadCharacteristic = null
        resetHitDetector()
        setStatus("正在连接 ${device.address}", "#40AAFF")
        appendLog("连接 ${device.address}")
        bluetoothGatt =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(this, false, gattCallback)
            }
        selectedDevice = device
        updateButtons()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt(updateStatus: Boolean = true) {
        manualDisconnect = true
        reconnectAttempts = 0
        cancelServiceDiscoveryTimeout()
        bluetoothGatt?.let {
            runCatching { writeGyroCommand(it, enabled = false) }
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        bluetoothGatt = null
        servicesDiscoveryStarted = false
        serviceDiscoveryRetryCount = 0
        pendingReadCharacteristic = null
        resetHitDetector()
        if (updateStatus) {
            setStatus("已断开", "#FFD060")
            appendLog("断开连接")
        }
        updateButtons()
    }

    private val gattCallback =
        object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (bluetoothGatt !== gatt && newState != BluetoothProfile.STATE_DISCONNECTED) {
                    runOnUiThread { appendLog("忽略旧 GATT 状态 status=$status state=$newState") }
                    return
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    reconnectAttempts = 0
                    runOnUiThread {
                        setStatus("已连接，正在发现服务", "#39D98A")
                        appendLog("GATT 已连接")
                    }
                    mainHandler.postDelayed({
                        if (bluetoothGatt === gatt) {
                            discoverServicesOnce(gatt)
                        }
                    }, SERVICE_DISCOVERY_START_DELAY_MS)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    val wasCurrent = bluetoothGatt === gatt
                    runCatching { gatt.close() }
                    runOnUiThread {
                        if (wasCurrent) {
                            bluetoothGatt = null
                            servicesDiscoveryStarted = false
                            serviceDiscoveryRetryCount = 0
                            cancelServiceDiscoveryTimeout()
                            setStatus("设备已断开", "#FFAA40")
                            appendLog("GATT 已断开 status=$status")
                            updateButtons()
                            scheduleReconnectIfNeeded(status)
                        } else {
                            appendLog("旧 GATT 已断开 status=$status")
                        }
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (bluetoothGatt !== gatt) {
                    runOnUiThread { appendLog("忽略旧 GATT 服务发现 status=$status") }
                    return
                }
                cancelServiceDiscoveryTimeout()
                servicesDiscoveryStarted = false
                val service = gatt.getService(serviceUuid)
                val notifyChar = service?.getCharacteristic(notifyUuid)
                val readChar = service?.getCharacteristic(readUuid)
                val writeChar = service?.getCharacteristic(writeUuid)
                runOnUiThread {
                    appendLog("服务发现 status=$status service=${service != null}")
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    runOnUiThread { setStatus("服务发现失败 status=$status，准备重连", "#FFAA40") }
                    forceReconnect("服务发现失败 status=$status")
                    return
                }
                if (service == null || notifyChar == null || writeChar == null) {
                    runOnUiThread { setStatus("未找到 FFE0/FFE1/FFE4 服务特征", "#FFAA40") }
                    return
                }
                pendingReadCharacteristic = readChar
                enableNotify(gatt, notifyChar)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (bluetoothGatt !== gatt) return
                runOnUiThread {
                    appendLog("写入 ${shortUuid(characteristic.uuid)} status=$status")
                }
                readPendingCharacteristic(gatt)
            }

            @Deprecated("Deprecated in Java")
            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                handleDescriptorWrite(gatt, descriptor, status)
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (bluetoothGatt !== gatt) return
                runOnUiThread {
                    appendLog("读取 ${shortUuid(characteristic.uuid)} status=$status ${characteristic.value?.toHex().orEmpty()}")
                }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                if (bluetoothGatt !== gatt) return
                runOnUiThread {
                    appendLog("读取 ${shortUuid(characteristic.uuid)} status=$status ${value.toHex()}")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (bluetoothGatt !== gatt) return
                handleNotify(characteristic.value ?: return)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (bluetoothGatt !== gatt) return
                handleNotify(value)
            }
        }

    @SuppressLint("MissingPermission")
    private fun discoverServicesOnce(gatt: BluetoothGatt) {
        if (servicesDiscoveryStarted || bluetoothGatt !== gatt) return
        servicesDiscoveryStarted = true
        runOnUiThread { appendLog("启动服务发现") }
        if (!gatt.discoverServices()) {
            servicesDiscoveryStarted = false
            runOnUiThread { setStatus("服务发现启动失败，准备重连", "#FFAA40") }
            forceReconnect("服务发现启动失败")
        } else {
            scheduleServiceDiscoveryTimeout(gatt)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scheduleServiceDiscoveryTimeout(gatt: BluetoothGatt) {
        cancelServiceDiscoveryTimeout()
        val runnable =
            Runnable {
                if (bluetoothGatt !== gatt || !servicesDiscoveryStarted) {
                    return@Runnable
                }
                servicesDiscoveryStarted = false
                if (serviceDiscoveryRetryCount < MAX_SERVICE_DISCOVERY_RETRIES) {
                    serviceDiscoveryRetryCount += 1
                    runOnUiThread {
                        appendLog("服务发现超时，重试 $serviceDiscoveryRetryCount/$MAX_SERVICE_DISCOVERY_RETRIES")
                    }
                    discoverServicesOnce(gatt)
                } else {
                    runOnUiThread { setStatus("服务发现超时，准备重连", "#FFAA40") }
                    forceReconnect("服务发现超时")
                }
            }
        serviceDiscoveryTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, SERVICE_DISCOVERY_TIMEOUT_MS)
    }

    private fun cancelServiceDiscoveryTimeout() {
        serviceDiscoveryTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        serviceDiscoveryTimeoutRunnable = null
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(cccdUuid)
        if (descriptor == null) {
            runOnUiThread { setStatus("FFE4 没有 CCCD 描述符", "#FFAA40") }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            runOnUiThread { appendLog("写入 CCCD 启动=$result") }
            if (result != BluetoothStatusCodes.SUCCESS) {
                runOnUiThread { setStatus("FFE4 CCCD 写入启动失败：$result", "#FFAA40") }
                return
            }
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            val started = gatt.writeDescriptor(descriptor)
            runOnUiThread { appendLog("写入 CCCD 启动=$started") }
            if (!started) {
                runOnUiThread { setStatus("FFE4 CCCD 写入启动失败", "#FFAA40") }
                return
            }
        }
        runOnUiThread {
            setStatus("正在打开 FFE4 通知", "#39D98A")
            appendLog("写入 FFE4 CCCD")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        status: Int,
    ) {
        if (descriptor.uuid != cccdUuid) return
        runOnUiThread {
            appendLog("CCCD 写入 status=$status")
            setStatus("通知已打开，陀螺仪保持关闭", "#39D98A")
        }
        readPendingCharacteristic(gatt)
    }

    @SuppressLint("MissingPermission")
    private fun writeGyroCommand(
        gatt: BluetoothGatt,
        enabled: Boolean,
    ): Boolean {
        if (bluetoothGatt !== gatt) return false
        val char = gatt.getService(serviceUuid)?.getCharacteristic(writeUuid) ?: return false
        val command = if (enabled) GYRO_ON_COMMAND else GYRO_OFF_COMMAND
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
        runOnUiThread {
            appendLog("${if (enabled) "开启" else "关闭"}陀螺仪指令启动=$started ${command.toHex()}")
        }
        return started
    }

    @SuppressLint("MissingPermission")
    private fun sendGyroCommandFromUi(enabled: Boolean) {
        val gatt = bluetoothGatt
        if (gatt == null) {
            setStatus("请先连接蓝牙设备", "#FFAA40")
            appendLog("手动${if (enabled) "开启" else "关闭"}失败：未连接")
            return
        }
        val started = writeGyroCommand(gatt, enabled)
        setStatus(
            if (started) {
                "已发送${if (enabled) "开启" else "关闭"}陀螺仪指令"
            } else {
                "${if (enabled) "开启" else "关闭"}陀螺仪指令发送失败"
            },
            if (started) "#39D98A" else "#FFAA40",
        )
    }

    @SuppressLint("MissingPermission")
    private fun readPendingCharacteristic(gatt: BluetoothGatt) {
        if (bluetoothGatt !== gatt) return
        val readChar = pendingReadCharacteristic ?: return
        pendingReadCharacteristic = null
        val started = gatt.readCharacteristic(readChar)
        runOnUiThread { appendLog("读取 FFE1 启动=$started") }
    }

    @SuppressLint("MissingPermission")
    private fun forceReconnect(reason: String) {
        val gatt = bluetoothGatt
        runOnUiThread { appendLog("$reason，关闭当前 GATT") }
        bluetoothGatt = null
        servicesDiscoveryStarted = false
        serviceDiscoveryRetryCount = 0
        cancelServiceDiscoveryTimeout()
        pendingReadCharacteristic = null
        gatt?.let {
            runCatching { it.disconnect() }
            runCatching { it.close() }
        }
        scheduleReconnectIfNeeded(GATT_INTERNAL_RECONNECT_STATUS)
    }

    @SuppressLint("MissingPermission")
    private fun scheduleReconnectIfNeeded(status: Int) {
        val device = reconnectDevice ?: selectedDevice ?: return
        if (manualDisconnect || status == 0 || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            return
        }
        reconnectAttempts += 1
        val attempt = reconnectAttempts
        runOnUiThread {
            setStatus("蓝牙断开，正在自动重连 $attempt/$MAX_RECONNECT_ATTEMPTS", "#FFD060")
            appendLog("自动重连 $attempt/$MAX_RECONNECT_ATTEMPTS")
            updateButtons()
        }
        mainHandler.postDelayed({
            if (manualDisconnect || bluetoothGatt != null) {
                return@postDelayed
            }
            notifyBuffer.clear()
            notifyDebugCount = 0
            pendingReadCharacteristic = null
            servicesDiscoveryStarted = false
            serviceDiscoveryRetryCount = 0
            bluetoothGatt =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(this, false, gattCallback)
                }
        }, RECONNECT_DELAY_MS)
    }

    private fun handleNotify(value: ByteArray) {
        synchronized(notifyBuffer) {
            if (notifyDebugCount < 12) {
                notifyDebugCount++
                appendLog("Notify len=${value.size} head=${value.take(12).toByteArray().toHex()}")
            }
            value.forEach { notifyBuffer.addLast(it) }
            while (notifyBuffer.size >= 3) {
                while (notifyBuffer.size >= 2 && !(notifyBuffer[0] == 0xD5.toByte() && notifyBuffer[1] == 0x5D.toByte())) {
                    notifyBuffer.removeFirst()
                }
                if (notifyBuffer.size < 3) return
                val command = notifyBuffer[2].u()
                if (command != COMMAND_ID) {
                    appendLog("未知帧命令 0x${command.toString(16).uppercase()}，丢弃帧头")
                    notifyBuffer.removeFirst()
                    continue
                }
                if (notifyBuffer.size < FRAME_LEN) return
                val frame = ByteArray(FRAME_LEN) { notifyBuffer.removeFirst() }
                parsePacket(frame)?.let { packet ->
                    runOnUiThread { renderPacket(packet) }
                }
            }
        }
    }

    private fun parsePacket(frame: ByteArray): BoxingPacket? {
        if (frame.size != FRAME_LEN || frame[0] != 0xD5.toByte() || frame[1] != 0x5D.toByte() || frame[2] != COMMAND_ID.toByte()) return null
        return BoxingPacket(
            rawHex = frame.toHex(),
            command = frame[2].u(),
            frameSeq = frame[3].u(),
            sampleRate = 0,
            batchSize = 1,
            powerState = frame[4].u(),
            flags = 0,
            reserved = 0,
            punches = frame[5].u(),
            samples =
                listOf(
                    BoxingSample(
                        ax = 0,
                        ay = 0,
                        az = 0,
                        roll = 0,
                        pitch = 0,
                        yaw = 0,
                        pressure = frame[7].u(),
                    ),
                ),
        )
    }

    private fun renderPacket(packet: BoxingPacket) {
        detectHitFromPunchCount(packet)
        val latest = packet.samples.lastOrNull() ?: return
        batteryValue.text = "电源状态\n${packet.powerState}"
        chargeValue.text = "帧序号\n${packet.frameSeq}"
        pressureValue.text = "拳击力度\n${latest.pressure}"
        punchValue.text = "拳击次数\n${packet.punches}"
        accelValue.text = "有效数据\n数据0 包数 ${packet.frameSeq}    数据1 电量 ${packet.powerState}    数据2 次数 ${packet.punches}    数据4 力度 ${latest.pressure}"
        angleValue.text = "忽略数据\n数据3 / 数据5 / 数据6 / 数据7 已忽略"
        rawValue.text = "原始帧\n${packet.rawHex}"
        val now = System.currentTimeMillis()
        if (now - lastPacketLogAtMs >= 1000L) {
            lastPacketLogAtMs = now
            appendLog("cmd=0x${packet.command.toString(16).uppercase()} seq=${packet.frameSeq} battery=${packet.powerState} gyroPunches=${packet.punches} gyroForce=${latest.pressure}")
        }
    }

    private fun detectHitFromPunchCount(packet: BoxingPacket) {
        val previous = lastPunchCount
        lastPunchCount = packet.punches
        if (previous == null) return
        if (packet.punches > previous || previous > packet.punches) {
            flashHitButton()
            appendLog("击中触发 punch_count $previous -> ${packet.punches}")
        }
    }

    private fun resetHitDetector() {
        lastPunchCount = null
        lastHitFlashAtMs = 0L
    }

    private fun flashHitButton(force: Boolean = false) {
        if (!::hitButton.isInitialized) return
        val now = System.currentTimeMillis()
        if (!force && now - lastHitFlashAtMs < HIT_FLASH_COOLDOWN_MS) return
        lastHitFlashAtMs = now
        hitButton.background = rounded("#D71920", "#FFB3B3", 18)
        hitButton.setTextColor(Color.WHITE)
        hitButton.alpha = 1f
        mainHandler.postDelayed({
            if (::hitButton.isInitialized) {
                hitButton.background = rounded("#182B35", "#31596A", 18)
                hitButton.setTextColor(Color.WHITE)
            }
        }, 160L)
    }

    private fun chargeText(value: Int): String =
        when (value) {
            0 -> "未充电"
            1 -> "正在充电"
            2 -> "已充满"
            else -> value.toString()
        }

    private fun setStatus(text: String, color: String) {
        if (!::statusView.isInitialized) return
        statusView.text = text
        statusView.background = rounded(color, "#173245", 999)
    }

    private fun appendLog(text: String) {
        Log.d(TAG, text)
        if (!::logView.isInitialized) return
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val current = logView.text.toString()
        val next = "[$time] $text\n$current"
        logView.text = next.lines().take(16).joinToString("\n")
    }

    private fun updateButtons() {
        if (!::scanButton.isInitialized) return
        scanButton.isEnabled = !scanning
        connectButton.isEnabled = selectedDevice != null && bluetoothGatt == null
        disconnectButton.isEnabled = bluetoothGatt != null
        if (::gyroOnButton.isInitialized) {
            gyroOnButton.isEnabled = bluetoothGatt != null
            gyroOffButton.isEnabled = bluetoothGatt != null
        }
    }

    private fun title(text: String, sp: Float): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        }

    private fun body(text: String): TextView =
        TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#E7F2FB"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setLineSpacing(0f, 1.18f)
        }

    private fun chip(text: String): TextView =
        body(text).apply {
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

    private fun actionButton(text: String): Button =
        Button(this).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            background = rounded("#174154", "#52B9D5", 18)
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(dp(8), 0, dp(8), 0)
        }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded("#0E1C28", "#28475B", 18)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(12) }
        }

    private fun section(text: String): TextView =
        body(text).apply {
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(Color.parseColor("#FFF3D3"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(0, 0, 0, dp(10))
        }

    private fun metricRow(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(8))
        }

    private fun metric(label: String, value: String): TextView =
        body("$label\n$value").apply {
            gravity = Gravity.CENTER
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            background = rounded("#132D3B", "#24566C", 14)
            setPadding(dp(10), dp(14), dp(10), dp(14))
        }

    private fun wideMetric(label: String, value: String): TextView =
        body("$label\n$value").apply {
            setTextColor(Color.parseColor("#D9ECF8"))
            background = rounded("#0A1722", "#1D3A4C", 14)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) }
        }

    private fun weightedParams(weight: Float, right: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
            rightMargin = dp(right)
        }

    private fun rounded(fill: String, stroke: String, cornerDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerDp).toFloat()
            setColor(Color.parseColor(fill))
            setStroke(dp(1), Color.parseColor(stroke))
        }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()

    private fun Byte.u(): Int = toInt() and 0xFF

    private fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it.u()) }

    private fun uuid16(value: String): UUID =
        UUID.fromString("0000${value.lowercase(Locale.US)}-0000-1000-8000-00805f9b34fb")

    private fun shortUuid(uuid: UUID): String = uuid.toString().substring(4, 8).uppercase(Locale.US)

    private data class DeviceCandidate(
        val device: BluetoothDevice,
        val name: String,
        val address: String,
        val rssi: Int,
    )

    private data class BoxingPacket(
        val rawHex: String,
        val command: Int,
        val frameSeq: Int,
        val sampleRate: Int,
        val batchSize: Int,
        val powerState: Int,
        val flags: Int,
        val reserved: Int,
        val punches: Int,
        val samples: List<BoxingSample>,
    )

    private data class BoxingSample(
        val ax: Int,
        val ay: Int,
        val az: Int,
        val roll: Int,
        val pitch: Int,
        val yaw: Int,
        val pressure: Int,
    )

    private companion object {
        const val TAG = "BoxingBleDebug"
        const val FRAME_LEN = 11
        const val COMMAND_ID = 0x03
        val GYRO_ON_COMMAND = byteArrayOf(0xC5.toByte(), 0x5C, 0x04, 0x01)
        val GYRO_OFF_COMMAND = byteArrayOf(0xC5.toByte(), 0x5C, 0x04, 0x00)
        const val HIT_FLASH_COOLDOWN_MS = 120L
        const val SERVICE_DISCOVERY_START_DELAY_MS = 650L
        const val MAX_SERVICE_DISCOVERY_RETRIES = 2
        const val SERVICE_DISCOVERY_TIMEOUT_MS = 4_000L
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val RECONNECT_DELAY_MS = 900L
        const val GATT_INTERNAL_RECONNECT_STATUS = 133
    }
}

