from datetime import datetime
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import HRFlowable, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


ROOT = Path(r"D:\2026\202604\0425")
OUT = ROOT / "光影破壁者_Light-Breaker_设计方案.pdf"

pdfmetrics.registerFont(TTFont("CN-Regular", r"C:\Windows\Fonts\simkai.ttf"))
pdfmetrics.registerFont(TTFont("CN-Bold", r"C:\Windows\Fonts\simhei.ttf"))

PAGE_W, PAGE_H = A4
styles = getSampleStyleSheet()
styles.add(
    ParagraphStyle(
        name="CNTitle",
        fontName="CN-Bold",
        fontSize=25,
        leading=32,
        alignment=TA_CENTER,
        textColor=colors.HexColor("#111827"),
        spaceAfter=10,
    )
)
styles.add(
    ParagraphStyle(
        name="CNSubTitle",
        fontName="CN-Regular",
        fontSize=11,
        leading=17,
        alignment=TA_CENTER,
        textColor=colors.HexColor("#4B5563"),
        spaceAfter=18,
    )
)
styles.add(
    ParagraphStyle(
        name="CNHeading1",
        fontName="CN-Bold",
        fontSize=15,
        leading=22,
        textColor=colors.HexColor("#0F766E"),
        spaceBefore=12,
        spaceAfter=8,
    )
)
styles.add(
    ParagraphStyle(
        name="CNBody",
        fontName="CN-Regular",
        fontSize=10.2,
        leading=17,
        textColor=colors.HexColor("#1F2937"),
        spaceAfter=5,
    )
)
styles.add(
    ParagraphStyle(
        name="CNBullet",
        fontName="CN-Regular",
        fontSize=10,
        leading=16,
        leftIndent=12,
        firstLineIndent=-8,
        textColor=colors.HexColor("#1F2937"),
        spaceAfter=3,
    )
)
styles.add(
    ParagraphStyle(
        name="CNQuote",
        fontName="CN-Bold",
        fontSize=12,
        leading=20,
        alignment=TA_CENTER,
        textColor=colors.white,
        backColor=colors.HexColor("#0F766E"),
        borderPadding=8,
        spaceBefore=8,
        spaceAfter=12,
    )
)
styles.add(
    ParagraphStyle(
        name="TableHead",
        fontName="CN-Bold",
        fontSize=9.5,
        leading=13,
        textColor=colors.white,
        alignment=TA_CENTER,
    )
)
styles.add(
    ParagraphStyle(
        name="TableCell",
        fontName="CN-Regular",
        fontSize=8.8,
        leading=13,
        textColor=colors.HexColor("#111827"),
    )
)


def p(text, style="CNBody"):
    return Paragraph(text, styles[style])


def bullets(items):
    story = []
    for item in items:
        story.append(p("• " + item, "CNBullet"))
    return story


def section(title):
    return [
        Spacer(1, 4),
        p(title, "CNHeading1"),
        HRFlowable(width="100%", thickness=0.6, color=colors.HexColor("#D1D5DB")),
        Spacer(1, 4),
    ]


def make_table(rows, col_widths):
    data = []
    for i, row in enumerate(rows):
        style = "TableHead" if i == 0 else "TableCell"
        data.append([p(str(cell), style) for cell in row])
    table = Table(data, colWidths=col_widths, hAlign="LEFT", repeatRows=1)
    table.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#0F766E")),
                ("BOX", (0, 0), (-1, -1), 0.6, colors.HexColor("#CBD5E1")),
                ("INNERGRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#E5E7EB")),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#F8FAFC")]),
            ]
        )
    )
    return table


def header_footer(canvas, doc):
    canvas.saveState()
    canvas.setFont("CN-Regular", 8)
    canvas.setFillColor(colors.HexColor("#6B7280"))
    canvas.drawString(18 * mm, PAGE_H - 12 * mm, "BoxingFitness 新模式方案 | 光影破壁者 Light-Breaker")
    canvas.drawRightString(PAGE_W - 18 * mm, PAGE_H - 12 * mm, datetime.now().strftime("%Y-%m-%d"))
    canvas.setStrokeColor(colors.HexColor("#E5E7EB"))
    canvas.line(18 * mm, PAGE_H - 15 * mm, PAGE_W - 18 * mm, PAGE_H - 15 * mm)
    canvas.drawCentredString(PAGE_W / 2, 11 * mm, f"第 {doc.page} 页")
    canvas.restoreState()


story = []
story.append(p("光影破壁者", "CNTitle"))
story.append(p("Light-Breaker：视觉拆盲盒拳击互动模式设计方案", "CNSubTitle"))
story.append(
    p(
        "通过蓝牙拳击设备采集命中事件，让玩家以挥拳方式击碎覆盖在画面上的虚拟瓷砖，逐步揭开隐藏艺术作品，并在过程中获得碎裂反馈、奖励道具、成就收藏与多人协作体验。",
        "CNQuote",
    )
)

story += section("一、产品定位")
story += bullets(
    [
        "模式定位：BoxingFitness 中的沉浸式视觉训练玩法，兼具健身、游戏、艺术揭晓和社交传播属性。",
        "目标场景：家庭娱乐、儿童训练、健身房团课、品牌活动大屏、亲子互动和轻竞技挑战。",
        "核心价值：把“拳击计数”转化为可见的视觉进度，让每一次击中都有即时反馈和长期收藏价值。",
    ]
)
story.append(p("一句话玩法：玩家通过挥拳击碎覆盖在画面上的瓷砖，逐步揭开隐藏画作；瓷砖有不同硬度，击中越快、越准、越有力，揭开的效果越华丽。"))

story += section("二、核心玩法：视觉拆盲盒")
story += bullets(
    [
        "画面生成：系统根据参与者偏好、节日主题、每日主题或运营活动，选择一幅艺术画、风景照、萌宠图、科幻海报或挑战卡。第一阶段建议先使用内置图库，后续再接入 AI 生成或云端下发。",
        "瓷砖遮罩：底图被切分为数百个虚拟瓷砖，例如 20 × 15 = 300 块。游戏开始时玩家只能看到瓷砖层，看不到完整底图。",
        "物理交互：蓝牙设备上报 punch_count 增加时，系统判定一次有效击中，对当前目标瓷砖造成伤害。",
        "破碎反馈：瓷砖生命值归零后触发裂纹、飞散、透明消失和碎裂音效，露出底图对应区域。",
        "动态难度：边缘瓷砖 1 击可破；普通瓷砖 2 击；中心关键瓷砖、宝箱瓷砖或锁定瓷砖需要 3 连击、强力击或组合条件。",
    ]
)

story += section("三、娱乐性：随机性与感官反馈")
story.append(
    make_table(
        [
            ["模块", "设计内容", "落地建议"],
            ["打击音效", "普通碎裂、强力碎裂、宝箱掉落、完成整幅画等多层音效。", "先内置 4 组 wav/mp3，命中时低延迟播放。"],
            ["盲盒惊喜", "玩家完全揭开前不知道底图是什么，可能是萌宠、风景、科幻海报，也可能是“再来一组”挑战卡。", "第一阶段用本地图片池随机；第二阶段支持主题包。"],
            ["视觉奖励", "揭开 50% 自动加流光滤镜，80% 加粒子和全屏高亮，100% 展示完整作品。", "Canvas 自绘或独立 GameView，先做 alpha/scale/particle。"],
            ["宝箱瓷砖", "击碎后掉落虚拟道具，例如范围震落、双倍经验、快速破壁等。", "控制比例 3%-5%，避免奖励过密。"],
        ],
        [28 * mm, 78 * mm, 70 * mm],
    )
)

story += section("四、成就感：多维度数字化反馈")
story += bullets(
    [
        "消耗可视化：把“击碎瓷砖数”与运动消耗挂钩，例如“今日击碎 200 块瓷砖，相当于消耗 1 个汉堡的热量”。",
        "艺术收藏：完整揭开的作品自动进入手机 App 的“我的画廊”，作为汗水结晶勋章，可分享给社交平台。",
        "破壁者等级：根据击打次数、完成率、连击、强力击和宝箱收集获得 XP，提升“破壁者”等级。",
        "技能树解锁：Lv1 普通陶瓷碎裂，Lv3 火花碎裂，Lv5 冰晶碎裂，Lv8 火焰碎裂，Lv10 星光破壁。",
    ]
)

story += section("五、协作感：共绘蓝图多人模式")
story += bullets(
    [
        "区域分工：多人并排站立，每人负责画面的一个象限或区域。只有团队共同推进，才能揭开完整作品。",
        "连击加成：当多名成员在 1 秒内同时击中各自区域时，触发“全场清屏”技能，随机震落一片瓷砖。",
        "接力解谜：部分锁定瓷砖需要 A 玩家完成组合拳，才能解锁 B 玩家面前的关键拼图块。",
        "团队排行榜：记录团队完成整幅作品的总时长、同步命中次数、平均贡献度，展示“最默契团队”。",
        "落地优先级：多人模式建议作为第三阶段能力，第一版先完成单人可玩闭环。",
    ]
)

story += section("六、技术架构")
story.append(p("建议在当前 Android 项目中新增独立模式，复用已经抽出的 BoxingBleManager。蓝牙层只负责事件和数据，游戏层负责瓷砖、动画、音效和结算。"))
story.append(
    make_table(
        [
            ["模块", "职责"],
            ["BoxingBleManager", "扫描 BOXING 设备、连接 GATT、打开 FFE4 Notify、向 FFE1 写开启/关闭陀螺仪指令、解析协议包、输出 onHit。"],
            ["LightBreakerActivity", "模式入口、权限处理、蓝牙连接控制、开始/暂停/结算流程。"],
            ["LightBreakerGameView", "绘制底图、瓷砖层、裂纹、粒子、滤镜、完成动画。"],
            ["TileGrid / Tile", "维护瓷砖坐标、生命值、类型、是否破碎、奖励状态。"],
            ["EffectEngine", "管理碎裂、清屏、流光、粒子、宝箱等即时反馈。"],
            ["GalleryStore", "保存完成作品、成绩、时间、分享图和收藏记录。"],
        ],
        [45 * mm, 131 * mm],
    )
)

story += section("七、命中映射与难度")
story += bullets(
    [
        "当前硬件第一版建议使用 punch_count +1 作为有效击中事件，避免手机端再做 AX/AY/AZ 命中判断。",
        "如果没有多靶位空间信息，第一版采用“智能选砖”：从未破碎区域里按规则选择目标瓷砖。",
        "压力值高时优先攻击中心或高硬度瓷砖；连击快时触发小范围震落；宝箱瓷砖按概率混入。",
        "后续如硬件提供多靶位、方向或区域信息，再把击中映射到具体画面区域。",
    ]
)
story.append(
    make_table(
        [
            ["难度", "瓷砖数量", "硬度规则", "适用场景"],
            ["简单", "约 150 块", "多数 1 击破碎，少量 2 击。", "儿童、首次体验、亲子互动。"],
            ["标准", "约 300 块", "边缘 1 击，普通 2 击，中心 3 击。", "60 秒训练、日常玩法。"],
            ["挑战", "约 500 块", "加入锁定瓷砖、宝箱瓷砖、连击清屏。", "高强度训练、活动竞技。"],
        ],
        [28 * mm, 35 * mm, 70 * mm, 43 * mm],
    )
)

story += section("八、MVP 落地范围")
story += bullets(
    [
        "新增 LightBreakerActivity 与 LightBreakerGameView。",
        "内置 10-20 张底图资源，先不依赖云端或 AI 生成。",
        "实现 20 × 15 瓷砖遮罩、瓷砖生命值、击中破碎、宝箱瓷砖、完成率统计。",
        "复用 BoxingBleManager，使用硬件 punch_count 增量触发击中。",
        "加入扫描、连接、断开、开启/关闭陀螺仪按钮。",
        "实现 50%、80%、100% 三个阶段的视觉奖励。",
        "结算页展示击碎数、拳击次数、完成率、估算卡路里和作品预览。",
    ]
)

story += section("九、开发阶段计划")
story.append(
    make_table(
        [
            ["阶段", "目标", "核心交付"],
            ["阶段 1：单人可玩版", "打通蓝牙到破砖的主链路。", "扫描连接、击中破砖、完成率、基础结算。"],
            ["阶段 2：视觉增强版", "提升爽感和随机奖励。", "碎裂动画、粒子、滤镜、宝箱、音效。"],
            ["阶段 3：收藏与成长", "建立长期留存。", "我的画廊、分享图、破壁者等级、技能树。"],
            ["阶段 4：多人协作", "形成核心差异化亮点。", "区域分工、合击清屏、接力解谜、团队排行榜。"],
        ],
        [36 * mm, 62 * mm, 78 * mm],
    )
)

story += section("十、关键风险与对策")
story.append(
    make_table(
        [
            ["风险", "表现", "对策"],
            ["硬件无区域信息", "无法判断击中画面哪个位置。", "第一版智能选砖；后续按多靶位或方向数据升级。"],
            ["画面过复杂导致性能下降", "低端手机动画卡顿。", "限制瓷砖数量，粒子池复用，按设备性能降级特效。"],
            ["玩法机械重复", "玩家只是在刷击中次数。", "加入宝箱、连击、阶段滤镜、挑战卡和收藏目标。"],
            ["多人同步复杂", "联网状态和设备连接不稳定。", "先做本地同屏多人，再扩展云端房间。"],
        ],
        [42 * mm, 54 * mm, 80 * mm],
    )
)

story += section("十一、推荐下一步")
story += bullets(
    [
        "在当前 Android 项目中新建 LightBreakerActivity.kt 与 LightBreakerGameView.kt。",
        "直接复用 BoxingBleManager.kt，先实现单人模式闭环。",
        "入口放在 BoxingFitness 主界面训练模式区，按钮名称为“光影破壁者”。",
        "第一版目标不是完整商业化，而是在真实蓝牙设备下验证：击中反馈是否爽、揭图过程是否有期待感、结算作品是否有分享欲。",
    ]
)
story.append(Spacer(1, 12))
story.append(
    p(
        "结论：光影破壁者适合作为 BoxingFitness 的新一代视觉化训练模式。建议以“单人可玩版”作为首个落地版本，先复用现有蓝牙链路和硬件 punch_count，再逐步扩展特效、收藏和多人协作。",
        "CNQuote",
    )
)

doc = SimpleDocTemplate(
    str(OUT),
    pagesize=A4,
    rightMargin=18 * mm,
    leftMargin=18 * mm,
    topMargin=22 * mm,
    bottomMargin=18 * mm,
    title="光影破壁者 Light-Breaker 设计方案",
    author="BoxingFitness",
)
doc.build(story, onFirstPage=header_footer, onLaterPages=header_footer)
print(OUT)
