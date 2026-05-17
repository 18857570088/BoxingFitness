package com.zclei.boxingfitness

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.text.SpannableString
import android.text.Spanned
import android.text.InputType
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import com.zclei.boxingfitness.cloud.CloudTrainingUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val fatBurnCoachFrMap =
    mapOf(
        "Fat Burn Coach" to "Coach Brûle-Graisse",
        "AI coach creates today's plan from your recent training performance" to "Le coach IA crée le plan du jour à partir de vos entraînements récents",
        "TODAY'S COURSE" to "COURS DU JOUR",
        "PROGRESS" to "PROGRÈS",
        "Today" to "Aujourd'hui",
        "Total" to "Total",
        "Hits" to "Coups",
        "Burn" to "Dépense",
        "Fat" to "Graisse",
        "Streak" to "Série",
        "Coach Plan" to "Plan du coach",
        "Reports" to "Rapports",
        "Milestones" to "Jalons",
        "Calories" to "Calories",
        "Fat Burn" to "Graisse brûlée",
        "Timer" to "Temps",
        "Open Report" to "Voir la tendance",
        "Back Home" to "Retour au coach",
        "AI coach builds today's fat-loss plan from your recent day, week, and month data." to "Le coach IA construit le plan brûle-graisse du jour à partir de vos données du jour, de la semaine et du mois.",
        "AI Coach Recommendation" to "Conseil du coach IA",
        "The coach uses your daily, weekly, and monthly load to shape today's recommendation." to "Le coach s'appuie sur vos charges du jour, de la semaine et du mois pour construire la recommandation du jour.",
        "Accept Advice" to "Accepter",
        "Re-evaluate" to "Réévaluer",
        "Body Metrics" to "Données corporelles",
        "Log today's weight and waist so the coach can adjust the plan with body-shape progress." to "Enregistrez le poids et le tour de taille du jour pour ajuster le plan à l'évolution corporelle.",
        "Today's Guided Plan" to "Plan guidé du jour",
        "Accept the coach's advice to lock today's plan and continue the coaching loop after the session." to "Acceptez le conseil du coach pour verrouiller le plan du jour et poursuivre l'accompagnement après la séance.",
        "Accepted" to "Accepté",
        "Preview" to "Aperçu",
        "Plan Basis" to "Base du plan",
        "A transparent breakdown of why today's load looks the way it does." to "Une explication claire de la raison pour laquelle la charge du jour est définie ainsi.",
        "Locked" to "Verrouillé",
        "Pending" to "En attente",
        "Daily / Weekly / Monthly Summary" to "Bilan jour / semaine / mois",
        "Calories, fat burn, and weight trend are stored in local reports." to "Les calories, la combustion des graisses et l'évolution du poids sont enregistrées dans les rapports locaux.",
        "Recent 7-Day Trend" to "Tendance des 7 derniers jours",
        "Track recent hits, calories, and fat-burn trends." to "Suivez les coups, calories et tendances de combustion des graisses.",
        "Turn long-term fat loss into visible small wins." to "Transformez la perte de graisse à long terme en petites victoires visibles.",
        "Weight kg" to "Poids kg",
        "Waist cm" to "Taille cm",
        "Save Metrics" to "Enregistrer",
        "Completed" to "Jours réalisés",
        "Avg hits" to "Moy. coups",
        "Avg kcal" to "Moy. kcal",
        "Weight" to "Poids",
        "Weight delta" to "Delta poids",
        "Waist" to "Taille",
        "Waist delta" to "Delta taille",
        "Rest" to "Repos",
        "Saved" to "Enregistré",
        "Unlocked" to "Déverrouillé",
        "Accept Advice First" to "Acceptez d'abord",
        "Replay Today's Session" to "Refaire la séance du jour",
        "Start Today's Session" to "Commencer la séance du jour",
        "Calibration complete. Punch whenever a target is on screen." to "Calibration terminée. Frappez dès qu'une cible apparaît à l'écran.",
        "Review today's class and start when ready." to "Consultez le cours du jour puis commencez quand vous êtes prêt.",
        "Starter Adapt" to "Adaptation initiale",
        "Light Activation" to "Activation légère",
        "Light Load" to "Charge légère",
        "Recovery Reset" to "Réinitialisation récupération",
        "Rhythm Reset" to "Réinitialisation du rythme",
        "Moderate-Light" to "Modéré-léger",
        "Build Forward" to "Montée en charge",
        "Metabolic Push" to "Poussée métabolique",
        "Medium-High" to "Moyen-élevé",
        "Speed Stimulus" to "Stimulation vitesse",
        "Cadence Lift" to "Hausse de cadence",
        "High Cadence" to "Cadence élevée",
        "Endurance Solidify" to "Consolidation endurance",
        "Long-Set Burn" to "Brûlage longue série",
        "High Intensity" to "Haute intensité",
        "Steady Progress" to "Progression stable",
        "Balanced Burn" to "Brûlage équilibré",
        "Moderate" to "Modéré",
        "Steady Fat Burn" to "Brûlage stable",
        "Steady Burn" to "Brûlage stable",
        "Stabilize breathing and rhythm before lifting the volume." to "Stabiliser la respiration et le rythme avant d'augmenter le volume.",
        "Rebuild total hits first, then lift intensity." to "Reconstruire d'abord le total de coups, puis remonter l'intensité.",
        "Recent training is stable, so today raises volume and sustained work." to "L'entraînement récent est stable, donc aujourd'hui augmente le volume et le travail continu.",
        "Raise pace with fast but stable combinations." to "Augmenter le rythme avec des combinaisons rapides mais stables.",
        "Your streak is strong, so today lengthens rounds to deepen endurance." to "Votre série est solide, donc aujourd'hui allonge les rounds pour approfondir l'endurance.",
        "Stay sustainable, stable, and consistent while balancing total hits and burn." to "Restez durable, stable et constant tout en équilibrant le total de coups et la dépense.",
        "Today's plan gently wakes up your body and rhythm." to "Le plan du jour réveille en douceur le corps et le rythme.",
        "This is your first coach session, so we start with a sustainable entry plan." to "C'est votre première séance avec le coach, donc nous commençons par un plan d'entrée durable.",
        "Coach note: stabilize rhythm first, then lift the load." to "Note du coach : stabilisez d'abord le rythme, puis augmentez la charge.",
        "Let's make today's session solid first, then log your weight and waist so the next plan can fit you better." to "Assurons d'abord une séance solide aujourd'hui, puis enregistrez votre poids et votre tour de taille pour mieux adapter le prochain plan.",
        "Your body trend is already moving forward, so today we keep the output steady and extend that momentum." to "Votre tendance corporelle progresse déjà, donc aujourd'hui nous gardons une sortie stable pour prolonger cet élan.",
        "Recent body metrics are moving in the right direction, so today's goal is to stay steady rather than rush." to "Les mesures corporelles récentes vont dans le bon sens ; l'objectif du jour est de rester stable plutôt que de se précipiter.",
        "No need to rush today. We rebuild total output first, then bring the intensity back." to "Pas besoin de se précipiter aujourd'hui. On reconstruit d'abord la sortie totale, puis on remet l'intensité.",
        "Your recent week looks solid, so today can nudge the total load upward while keeping the output stable." to "Votre dernière semaine est solide, donc aujourd'hui on peut augmenter légèrement la charge totale en gardant une sortie stable.",
        "Today's session leans into pace and cadence to bring your heart rate up and lock in burn." to "La séance du jour mise sur le rythme et la cadence pour élever la fréquence cardiaque et fixer la dépense.",
        "Your consistency is strong enough for longer work blocks today." to "Votre régularité permet aujourd'hui des blocs de travail plus longs.",
        "Today's job is to smooth out breathing and output before chasing more intensity." to "La mission du jour est de lisser la respiration et la sortie avant d'ajouter plus d'intensité.",
        "Coach note: finish today's class steadily, then record your weight and waist." to "Note du coach : terminez la séance du jour avec régularité, puis notez votre poids et votre tour de taille.",
        "Coach note: progress is already visible, so keep your output calm and controlled." to "Note du coach : les progrès sont déjà visibles, gardez donc une sortie calme et contrôlée.",
        "Coach note: spend the first rounds restoring breathing and total output before lifting pace." to "Note du coach : utilisez les premiers rounds pour retrouver respiration et volume total avant d'augmenter le rythme.",
        "Coach note: stay fast but clean. Every solid punch matters more than raw speed." to "Note du coach : restez rapide mais propre. Chaque coup solide compte plus que la vitesse brute.",
    )

private val fatBurnCoachThMap =
    mapOf(
        "Fat Burn Coach" to "โค้ชเผาผลาญไขมัน",
        "AI coach creates today's plan from your recent training performance" to "โค้ช AI สร้างแผนวันนี้จากผลการฝึกล่าสุดของคุณ",
        "TODAY'S COURSE" to "คอร์สวันนี้",
        "PROGRESS" to "ความคืบหน้า",
        "Today" to "วันนี้",
        "Total" to "สะสม",
        "Hits" to "ครั้งชก",
        "Burn" to "เผาผลาญ",
        "Fat" to "ไขมัน",
        "Streak" to "ต่อเนื่อง",
        "Coach Plan" to "แผนโค้ช",
        "Reports" to "รายงาน",
        "Milestones" to "หมุดหมาย",
        "Calories" to "แคลอรี",
        "Fat Burn" to "เผาผลาญไขมัน",
        "Timer" to "เวลา",
        "Open Report" to "ดูแนวโน้ม",
        "Back Home" to "กลับหน้าโค้ช",
        "AI coach builds today's fat-loss plan from your recent day, week, and month data." to "โค้ช AI สร้างแผนลดไขมันของวันนี้จากข้อมูลรายวัน รายสัปดาห์ และรายเดือนล่าสุดของคุณ",
        "AI Coach Recommendation" to "คำแนะนำจากโค้ช AI",
        "The coach uses your daily, weekly, and monthly load to shape today's recommendation." to "โค้ชใช้ภาระฝึกของวันนี้ สัปดาห์นี้ และเดือนนี้ เพื่อออกคำแนะนำของวันนี้",
        "Accept Advice" to "ยอมรับคำแนะนำ",
        "Re-evaluate" to "ประเมินใหม่",
        "Body Metrics" to "ข้อมูลร่างกาย",
        "Log today's weight and waist so the coach can adjust the plan with body-shape progress." to "บันทึกน้ำหนักและรอบเอววันนี้ เพื่อให้โค้ชปรับแผนตามความเปลี่ยนแปลงของรูปร่าง",
        "Today's Guided Plan" to "แผนโค้ชวันนี้",
        "Accept the coach's advice to lock today's plan and continue the coaching loop after the session." to "ยอมรับคำแนะนำของโค้ชเพื่อยืนยันแผนวันนี้ และให้ระบบติดตามต่อหลังจบเซสชัน",
        "Accepted" to "ยอมรับแล้ว",
        "Preview" to "ตัวอย่าง",
        "Plan Basis" to "เหตุผลของแผน",
        "A transparent breakdown of why today's load looks the way it does." to "อธิบายชัดเจนว่าทำไมภาระฝึกวันนี้จึงเป็นแบบนี้",
        "Locked" to "ล็อกแล้ว",
        "Pending" to "รอเริ่ม",
        "Daily / Weekly / Monthly Summary" to "สรุปรายวัน / รายสัปดาห์ / รายเดือน",
        "Calories, fat burn, and weight trend are stored in local reports." to "แคลอรี การเผาผลาญไขมัน และแนวโน้มน้ำหนักจะถูกเก็บไว้ในรายงานภายในเครื่อง",
        "Recent 7-Day Trend" to "แนวโน้ม 7 วันล่าสุด",
        "Track recent hits, calories, and fat-burn trends." to "ติดตามแนวโน้มจำนวนครั้งชก แคลอรี และการเผาผลาญไขมันล่าสุด",
        "Turn long-term fat loss into visible small wins." to "เปลี่ยนเป้าหมายลดไขมันระยะยาวให้เป็นชัยชนะเล็ก ๆ ที่เห็นผลได้",
        "Weight kg" to "น้ำหนัก kg",
        "Waist cm" to "รอบเอว cm",
        "Save Metrics" to "บันทึกข้อมูล",
        "Completed" to "วันที่สำเร็จ",
        "Avg hits" to "เฉลี่ยครั้งชก",
        "Avg kcal" to "เฉลี่ย kcal",
        "Weight" to "น้ำหนัก",
        "Weight delta" to "น้ำหนักเปลี่ยน",
        "Waist" to "รอบเอว",
        "Waist delta" to "เอวเปลี่ยน",
        "Rest" to "พัก",
        "Saved" to "บันทึกแล้ว",
        "Unlocked" to "ปลดล็อกแล้ว",
        "Accept Advice First" to "ยอมรับคำแนะนำก่อน",
        "Replay Today's Session" to "เล่นเซสชันวันนี้อีกครั้ง",
        "Start Today's Session" to "เริ่มเซสชันวันนี้",
        "Calibration complete. Punch whenever a target is on screen." to "ปรับเทียบเสร็จแล้ว ชกได้ทันทีเมื่อมีเป้าปรากฏบนหน้าจอ",
        "Review today's class and start when ready." to "ดูคอร์สวันนี้ก่อน แล้วเริ่มเมื่อพร้อม",
        "Starter Adapt" to "เริ่มต้นปรับตัว",
        "Light Activation" to "กระตุ้นเบา ๆ",
        "Light Load" to "โหลดเบา",
        "Recovery Reset" to "ฟื้นฟูรีเซ็ต",
        "Rhythm Reset" to "รีเซ็ตจังหวะ",
        "Moderate-Light" to "กลางค่อนเบา",
        "Build Forward" to "ยกระดับต่อเนื่อง",
        "Metabolic Push" to "เร่งเมตาบอลิซึม",
        "Medium-High" to "กลางถึงสูง",
        "Speed Stimulus" to "กระตุ้นความเร็ว",
        "Cadence Lift" to "เพิ่มความถี่",
        "High Cadence" to "ความถี่สูง",
        "Endurance Solidify" to "เสริมความอึด",
        "Long-Set Burn" to "เผาผลาญชุดยาว",
        "High Intensity" to "เข้มข้นสูง",
        "Steady Progress" to "พัฒนาอย่างมั่นคง",
        "Balanced Burn" to "เผาผลาญสมดุล",
        "Moderate" to "ปานกลาง",
        "Steady Fat Burn" to "เผาผลาญคงที่",
        "Steady Burn" to "เผาผลาญคงที่",
        "Stabilize breathing and rhythm before lifting the volume." to "ทำให้การหายใจและจังหวะนิ่งก่อน แล้วค่อยเพิ่มปริมาณ",
        "Rebuild total hits first, then lift intensity." to "เรียกจำนวนครั้งชกรวมกลับมาก่อน แล้วค่อยเพิ่มความเข้ม",
        "Recent training is stable, so today raises volume and sustained work." to "การฝึกล่าสุดค่อนข้างนิ่ง วันนี้จึงเพิ่มปริมาณและช่วงทำงานต่อเนื่อง",
        "Raise pace with fast but stable combinations." to "เพิ่มความเร็วด้วยคอมโบที่เร็วแต่ยังนิ่ง",
        "Your streak is strong, so today lengthens rounds to deepen endurance." to "สถิติต่อเนื่องของคุณดี วันนี้จึงเพิ่มความยาวของรอบเพื่อเสริมความอึด",
        "Stay sustainable, stable, and consistent while balancing total hits and burn." to "รักษาความต่อเนื่องและความนิ่ง พร้อมบาลานซ์จำนวนครั้งชกกับการเผาผลาญ",
        "Today's plan gently wakes up your body and rhythm." to "แผนวันนี้จะค่อย ๆ ปลุกร่างกายและจังหวะของคุณ",
        "This is your first coach session, so we start with a sustainable entry plan." to "นี่คือเซสชันแรกกับโค้ชของคุณ เราจึงเริ่มด้วยแผนเบื้องต้นที่ทำได้ต่อเนื่อง",
        "Coach note: stabilize rhythm first, then lift the load." to "คำแนะนำโค้ช: ทำจังหวะให้นิ่งก่อน แล้วค่อยเพิ่มโหลด",
        "Let's make today's session solid first, then log your weight and waist so the next plan can fit you better." to "มาทำให้เซสชันวันนี้มั่นคงก่อน แล้วค่อยบันทึกน้ำหนักและรอบเอวเพื่อให้แผนถัดไปเหมาะกับคุณยิ่งขึ้น",
        "Your body trend is already moving forward, so today we keep the output steady and extend that momentum." to "แนวโน้มร่างกายของคุณกำลังดีขึ้น วันนี้จึงเน้นปล่อยหมัดอย่างสม่ำเสมอเพื่อรักษาโมเมนตัม",
        "Recent body metrics are moving in the right direction, so today's goal is to stay steady rather than rush." to "ค่าร่างกายล่าสุดกำลังไปในทางที่ดี เป้าหมายวันนี้จึงคือความนิ่ง ไม่ใช่การเร่งจนเกินไป",
        "No need to rush today. We rebuild total output first, then bring the intensity back." to "วันนี้ไม่ต้องรีบ เราจะค่อย ๆ เรียกปริมาณการชกรวมกลับมาก่อน แล้วค่อยเพิ่มความเข้ม",
        "Your recent week looks solid, so today can nudge the total load upward while keeping the output stable." to "สัปดาห์ล่าสุดของคุณค่อนข้างดี วันนี้จึงเพิ่มโหลดรวมได้เล็กน้อยโดยยังคงความนิ่งของการชก",
        "Today's session leans into pace and cadence to bring your heart rate up and lock in burn." to "เซสชันวันนี้จะเน้นความเร็วและจังหวะเพื่อดันอัตราการเต้นหัวใจและคงการเผาผลาญ",
        "Your consistency is strong enough for longer work blocks today." to "ความสม่ำเสมอของคุณดีพอสำหรับช่วงทำงานที่ยาวขึ้นในวันนี้",
        "Today's job is to smooth out breathing and output before chasing more intensity." to "ภารกิจวันนี้คือทำให้การหายใจและการปล่อยหมัดลื่นไหล ก่อนจะไล่ความเข้มที่สูงขึ้น",
        "Coach note: finish today's class steadily, then record your weight and waist." to "คำแนะนำโค้ช: ทำคอร์สวันนี้ให้จบอย่างนิ่ง แล้วค่อยบันทึกน้ำหนักและรอบเอว",
        "Coach note: progress is already visible, so keep your output calm and controlled." to "คำแนะนำโค้ช: มีความก้าวหน้าให้เห็นแล้ว รักษาการชกให้สงบและควบคุมได้",
        "Coach note: spend the first rounds restoring breathing and total output before lifting pace." to "คำแนะนำโค้ช: ใช้ช่วงแรกเรียกจังหวะหายใจและปริมาณรวมกลับมาก่อน แล้วค่อยเพิ่มความเร็ว",
        "Coach note: stay fast but clean. Every solid punch matters more than raw speed." to "คำแนะนำโค้ช: เร็วได้แต่ต้องคม แต่ละหมัดที่ชัดเจนสำคัญกว่าความเร็วล้วน ๆ",
    )

private fun fatBurnCoachTranslate(languageCode: String, en: String): String =
    when {
        languageCode.startsWith("fr") -> fatBurnCoachFrMap[en] ?: en
        languageCode.startsWith("th") -> fatBurnCoachThMap[en] ?: en
        else -> en
    }

private fun fatBurnCoachText(
    languageCode: String,
    zh: String,
    en: String,
    fr: String? = null,
    th: String? = null,
): String =
    when {
        languageCode.startsWith("zh") -> zh
        languageCode.startsWith("fr") -> fr ?: fatBurnCoachTranslate(languageCode, en)
        languageCode.startsWith("th") -> th ?: fatBurnCoachTranslate(languageCode, en)
        else -> en
    }

private fun fatBurnCoachLocale(languageCode: String): Locale =
    when {
        languageCode.startsWith("zh") -> Locale.CHINA
        languageCode.startsWith("fr") -> Locale.FRANCE
        languageCode.startsWith("th") -> Locale("th", "TH")
        else -> Locale.US
    }

class FatBurnCoachActivity : AppCompatActivity(), BlitzModeGameView.Listener {
    private enum class DashboardTab {
        Plan,
        Report,
        Achievement,
    }

    private data class ChallengeDayPlan(
        val dayIndex: Int,
        val phaseLabelZh: String,
        val phaseLabelEn: String,
        val titleZh: String,
        val titleEn: String,
        val focusZh: String,
        val focusEn: String,
        val intensityZh: String,
        val intensityEn: String,
        val warmupSec: Int,
        val roundCount: Int,
        val workSec: Int,
        val restSec: Int,
        val cooldownSec: Int,
        val bpm: Int,
        val loadScore: Int,
        val estimatedCalories: Float,
        val targetValidHits: Int,
    ) {
        val totalDurationSec: Int
            get() = warmupSec + cooldownSec + roundCount * workSec + (roundCount - 1).coerceAtLeast(0) * restSec

        fun phaseLabel(languageCode: String): String = fatBurnCoachText(languageCode, phaseLabelZh, phaseLabelEn)

        fun title(languageCode: String): String = fatBurnCoachText(languageCode, titleZh, titleEn)

        fun focus(languageCode: String): String = fatBurnCoachText(languageCode, focusZh, focusEn)

        fun intensity(languageCode: String): String = fatBurnCoachText(languageCode, intensityZh, intensityEn)

        fun targetHps(): Float = targetValidHits / totalDurationSec.toFloat().coerceAtLeast(1f)

        fun toJson(): JSONObject =
            JSONObject()
                .put("dayIndex", dayIndex)
                .put("phaseLabelZh", phaseLabelZh)
                .put("phaseLabelEn", phaseLabelEn)
                .put("titleZh", titleZh)
                .put("titleEn", titleEn)
                .put("focusZh", focusZh)
                .put("focusEn", focusEn)
                .put("intensityZh", intensityZh)
                .put("intensityEn", intensityEn)
                .put("warmupSec", warmupSec)
                .put("roundCount", roundCount)
                .put("workSec", workSec)
                .put("restSec", restSec)
                .put("cooldownSec", cooldownSec)
                .put("bpm", bpm)
                .put("loadScore", loadScore)
                .put("estimatedCalories", estimatedCalories.toDouble())
                .put("targetValidHits", targetValidHits)

        companion object {
            fun fromJson(json: JSONObject): ChallengeDayPlan =
                ChallengeDayPlan(
                    dayIndex = json.optInt("dayIndex", 1),
                    phaseLabelZh = json.optString("phaseLabelZh"),
                    phaseLabelEn = json.optString("phaseLabelEn"),
                    titleZh = json.optString("titleZh"),
                    titleEn = json.optString("titleEn"),
                    focusZh = json.optString("focusZh"),
                    focusEn = json.optString("focusEn"),
                    intensityZh = json.optString("intensityZh"),
                    intensityEn = json.optString("intensityEn"),
                    warmupSec = json.optInt("warmupSec", 60),
                    roundCount = json.optInt("roundCount", 4),
                    workSec = json.optInt("workSec", 35),
                    restSec = json.optInt("restSec", 20),
                    cooldownSec = json.optInt("cooldownSec", 60),
                    bpm = json.optInt("bpm", 120),
                    loadScore = json.optInt("loadScore", 50),
                    estimatedCalories = json.optDouble("estimatedCalories", 80.0).toFloat(),
                    targetValidHits = json.optInt("targetValidHits", 140),
                )
        }
    }

    private data class CoachAdvice(
        val variantKey: String,
        val summaryZh: String,
        val summaryEn: String,
        val reasonZh: String,
        val reasonEn: String,
        val coachCueZh: String,
        val coachCueEn: String,
        val previewPlan: ChallengeDayPlan,
    ) {
        fun summary(languageCode: String): String = fatBurnCoachText(languageCode, summaryZh, summaryEn)

        fun reason(languageCode: String): String = fatBurnCoachText(languageCode, reasonZh, reasonEn)

        fun coachCue(languageCode: String): String = fatBurnCoachText(languageCode, coachCueZh, coachCueEn)
    }

    private data class DayReport(
        val dayIndex: Int,
        val timestampMs: Long,
        val totalHits: Int,
        val validHits: Int,
        val missedBeats: Int,
        val accuracy: Float,
        val hps: Float,
        val calories: Float,
        val fatBurnGrams: Float,
        val durationSec: Int,
        val grade: String,
        val estimatedWeightKg: Float,
        val estimatedWaistDeltaCm: Float,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("dayIndex", dayIndex)
                .put("timestampMs", timestampMs)
                .put("totalHits", totalHits)
                .put("validHits", validHits)
                .put("missedBeats", missedBeats)
                .put("accuracy", accuracy.toDouble())
                .put("hps", hps.toDouble())
                .put("calories", calories.toDouble())
                .put("fatBurnGrams", fatBurnGrams.toDouble())
                .put("durationSec", durationSec)
                .put("grade", grade)
                .put("estimatedWeightKg", estimatedWeightKg.toDouble())
                .put("estimatedWaistDeltaCm", estimatedWaistDeltaCm.toDouble())

        companion object {
            fun fromJson(json: JSONObject): DayReport =
                DayReport(
                    dayIndex = json.optInt("dayIndex"),
                    timestampMs = json.optLong("timestampMs"),
                    totalHits = json.optInt("totalHits"),
                    validHits = json.optInt("validHits"),
                    missedBeats = json.optInt("missedBeats"),
                    accuracy = json.optDouble("accuracy").toFloat(),
                    hps = json.optDouble("hps").toFloat(),
                    calories = json.optDouble("calories").toFloat(),
                    fatBurnGrams = json.optDouble("fatBurnGrams").toFloat(),
                    durationSec = json.optInt("durationSec"),
                    grade = json.optString("grade", "B"),
                    estimatedWeightKg = json.optDouble("estimatedWeightKg", DEFAULT_BASE_WEIGHT_KG.toDouble()).toFloat(),
                    estimatedWaistDeltaCm = json.optDouble("estimatedWaistDeltaCm", 0.0).toFloat(),
                )
        }
    }

    private data class AggregateWindow(
        val completedDays: Int,
        val calories: Float,
        val fatBurnGrams: Float,
        val avgHits: Float,
        val avgCaloriesPerSession: Float,
        val estimatedWeightKg: Float,
        val estimatedWaistDeltaCm: Float,
        val currentWaistCm: Float,
        val weightDeltaKg: Float,
    )

    private data class BodyMetricsEntry(
        val timestampMs: Long,
        val weightKg: Float,
        val waistCm: Float,
    ) {
        fun toJson(): JSONObject =
            JSONObject()
                .put("timestampMs", timestampMs)
                .put("weightKg", weightKg.toDouble())
                .put("waistCm", waistCm.toDouble())

        companion object {
            fun fromJson(json: JSONObject): BodyMetricsEntry =
                BodyMetricsEntry(
                    timestampMs = json.optLong("timestampMs"),
                    weightKg = json.optDouble("weightKg", DEFAULT_BASE_WEIGHT_KG.toDouble()).toFloat(),
                    waistCm = json.optDouble("waistCm", DEFAULT_BASE_WAIST_CM.toDouble()).toFloat(),
                )
        }
    }

    private data class AchievementMilestone(
        val titleZh: String,
        val titleEn: String,
        val descriptionZh: String,
        val descriptionEn: String,
        val unlocked: Boolean,
        val progress: Float,
        val progressLabel: String,
    ) {
        fun title(languageCode: String): String = fatBurnCoachText(languageCode, titleZh, titleEn)

        fun description(languageCode: String): String = fatBurnCoachText(languageCode, descriptionZh, descriptionEn)
    }

    private lateinit var rootContainer: FrameLayout
    private lateinit var gameView: BlitzModeGameView
    private lateinit var dashboardScrollView: ScrollView
    private lateinit var dashboardColumn: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var statusBarView: View
    private lateinit var backButtonView: View
    private lateinit var hintView: TextView
    private lateinit var countdownView: TextView
    private lateinit var startButtonView: TextView
    private lateinit var endButtonView: TextView
    private lateinit var resultPopupView: View
    private lateinit var resultTitleView: TextView
    private lateinit var resultBodyView: TextView
    private lateinit var resultPrimaryButtonView: TextView
    private lateinit var resultSecondaryButtonView: TextView

    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var todayDayView: TextView
    private lateinit var todayThemeView: TextView
    private lateinit var todayMetaView: TextView
    private lateinit var todayLoadView: TextView
    private lateinit var streakValueView: TextView
    private lateinit var weekValueView: TextView
    private lateinit var monthValueView: TextView
    private lateinit var trendValueView: TextView
    private lateinit var cumulativeCaloriesValueView: TextView
    private lateinit var cumulativeFatBurnValueView: TextView

    private lateinit var totalHitsValueView: TextView
    private lateinit var caloriesValueView: TextView
    private lateinit var fatBurnValueView: TextView
    private lateinit var timerValueView: TextView

    private val tabButtons = LinkedHashMap<DashboardTab, TextView>()

    private var selectedTab = DashboardTab.Plan
    private var trainingActive = false
    private var launchCountdownActive = false
    private var pendingStartAfterPermission = false
    private var remainingTrainingMs = 0L
    private lateinit var currentAdvice: CoachAdvice
    private var currentPlan = generatePlanForDay(1)
    private var lastSnapshot =
        FatBurnCoachGameView.OverlaySnapshot(
            totalHits = 0,
            validHits = 0,
            missedBeats = 0,
            accuracy = 0f,
            hps = 0f,
            combo = 0,
            bestCombo = 0,
            calories = 0f,
            fatBurnGrams = 0f,
            roundIndex = 0,
            roundTotal = 0,
            phaseLabel = "",
            beatFitLabel = "",
        )
    private var lastDetectorStateType = "loading"
    private var detectorRecoveryAttempts = 0
    private var trainingRunId = 0L
    private var speechEngine: TextToSpeech? = null
    private var speechReady = false

    private var detectorJob: Job? = null
    private var bleHitListener: BoxingBleRuntime.HitListener? = null
    private var bleImpactPreviewListener: BoxingBleRuntime.ImpactPreviewListener? = null
    private var detectorRecoveryJob: Job? = null
    private var countdownJob: Job? = null
    private var launchCountdownJob: Job? = null
    private var debugAutoFinishJob: Job? = null

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    private val sensitivityLevel by lazy {
        intent.getIntExtra(EXTRA_SENSITIVITY_LEVEL, 50)
    }
    private val languageCode by lazy {
        intent.getStringExtra(EXTRA_LANGUAGE).orEmpty().ifBlank { "zh" }
    }
    private val autoAcceptPlanForTest by lazy {
        intent.getBooleanExtra(EXTRA_AUTO_ACCEPT_PLAN, false)
    }
    private val autoStartSessionForTest by lazy {
        intent.getBooleanExtra(EXTRA_AUTO_START_SESSION, false)
    }
    private val autoSimulatePunchesForTest by lazy {
        intent.getBooleanExtra(EXTRA_AUTO_SIMULATE_PUNCHES, false)
    }
    private val autoFinishAfterMsForTest by lazy {
        intent.getLongExtra(EXTRA_AUTO_FINISH_AFTER_MS, 0L).coerceAtLeast(0L)
    }
    private val autoSeedWeightKgForTest by lazy {
        intent.getFloatExtra(EXTRA_AUTO_SEED_WEIGHT_KG, -1f)
    }
    private val autoSeedWaistCmForTest by lazy {
        intent.getFloatExtra(EXTRA_AUTO_SEED_WAIST_CM, -1f)
    }
    private val autoOpenTabForTest by lazy {
        intent.getStringExtra(EXTRA_AUTO_OPEN_TAB).orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        currentAdvice =
            CoachAdvice(
                variantKey = "starter",
                summaryZh = "今天先做一节轻量陪练，帮你把身体和节奏都唤醒。",
                summaryEn = "Today's plan gently wakes up your body and rhythm.",
                reasonZh = "首次进入陪练模块，先为你准备一套平稳易坚持的起步计划。",
                reasonEn = "This is your first coach session, so we start with a sustainable entry plan.",
                coachCueZh = "教练提醒：先稳定节奏，再逐步提高负荷。",
                coachCueEn = "Coach note: stabilize rhythm first, then lift the load.",
                previewPlan = currentPlan,
            )
        setContentView(buildContentView())
        hideSystemBars()
        initSpeechEngine()
        refreshDashboard()
        renderIdleState()
        applyAutomationLaunchOptions()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (trainingActive) {
            resumeTrainingIfNeeded()
        }
    }

    override fun onPause() {
        cancelLaunchCountdown(showMessage = false)
        pauseTrainingCountdown()
        stopDetectorSession()
        clearDetectorRecoveryState()
        cancelDebugAutoFinish()
        super.onPause()
    }

    override fun onDestroy() {
        if (trainingActive || launchCountdownActive) {
            BoxingBleRuntime.disableGyro()
        }
        cancelLaunchCountdown(showMessage = false)
        pauseTrainingCountdown()
        stopDetectorSession()
        clearDetectorRecoveryState()
        cancelDebugAutoFinish()
        speechEngine?.stop()
        speechEngine?.shutdown()
        speechEngine = null
        speechReady = false
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onOverlaySnapshot(snapshot: BlitzModeGameView.OverlaySnapshot) {
        lastSnapshot = snapshot.toCoachOverlaySnapshot()
        if (!::totalHitsValueView.isInitialized) {
            return
        }
        totalHitsValueView.text = snapshot.hits.toString()
        caloriesValueView.text = "${formatDecimal(snapshot.calories, 0)} kcal"
        fatBurnValueView.text = "${formatDecimal(snapshot.fatBurnGrams, 1)} g"
    }

    override fun onHintChanged(hint: String) {
        hintView.text = hint
    }

    private fun buildContentView(): View {
        rootContainer =
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
            }

        currentPlan = todayPlan()
        gameView =
            BlitzModeGameView(this).apply {
                setLanguageCode(languageCode)
                setDifficultyPreset(currentPlan.toBlitzDifficultyPreset())
                visibility = View.GONE
            }
        rootContainer.addView(
            gameView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val overlay =
            FrameLayout(this).apply {
                layoutParams =
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }

        backButtonView = buildBackButton()
        overlay.addView(
            backButtonView,
            FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.START).apply {
                leftMargin = dp(6)
                topMargin = dp(6)
            },
        )

        dashboardScrollView =
            ScrollView(this).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }
        dashboardColumn =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(72), dp(18), dp(16), dp(16))
            }
        dashboardScrollView.addView(
            dashboardColumn,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        buildDashboardContents()
        overlay.addView(
            dashboardScrollView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        statusBarView = buildStatusBar()
        overlay.addView(
            statusBarView,
            FrameLayout.LayoutParams(
                dp(470),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ).apply {
                topMargin = dp(18)
            },
        )

        endButtonView =
            commandButton(text("结束", "End"), fillColor = "#B93535", strokeColor = "#FF9186").apply {
                setOnClickListener { finishTrainingSession(manual = true) }
            }
        overlay.addView(
            endButtonView,
            FrameLayout.LayoutParams(
                dp(100),
                dp(44),
                Gravity.BOTTOM or Gravity.START,
            ).apply {
                leftMargin = dp(10)
                bottomMargin = dp(10)
            },
        )

        hintView =
            TextView(this).apply {
                setTextColor(Color.parseColor("#F2F7FB"))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(10), dp(18), dp(10))
                background = roundedFill("#4D0C2232", "#4DAEE8FF", 18)
            }
        overlay.addView(
            hintView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = dp(18)
            },
        )

        countdownView =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 54f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setShadowLayer(16f, 0f, 0f, Color.parseColor("#AAFF7A59"))
            }
        overlay.addView(
            countdownView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        resultPopupView = buildResultPopup()
        overlay.addView(
            resultPopupView,
            FrameLayout.LayoutParams(
                dp(520),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        overlay.bringChildToFront(backButtonView)

        rootContainer.addView(overlay)
        gameView.bind(this)
        return rootContainer
    }

    private fun buildDashboardContents() {
        titleView =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                text = text("燃脂陪练", "Fat Burn Coach")
            }
        dashboardColumn.addView(titleView)

        subtitleView =
            TextView(this).apply {
                setTextColor(Color.parseColor("#D0E8F5"))
                textSize = 13.5f
                text = text("AI 教练根据近期训练表现生成今日陪练计划", "AI coach creates today's plan from your recent training performance")
            }
        dashboardColumn.addView(subtitleView)
        dashboardColumn.addView(verticalSpace(12))

        val heroRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.TOP
            }
        heroRow.addView(
            buildTodayCard(),
            LinearLayout.LayoutParams(dp(340), dp(212)),
        )
        heroRow.addView(horizontalSpace(12))
        heroRow.addView(
            buildSummaryCard(),
            LinearLayout.LayoutParams(dp(228), dp(212)),
        )
        dashboardColumn.addView(heroRow)
        dashboardColumn.addView(verticalSpace(12))

        val actionRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
        startButtonView =
            commandButton(text("开始今日陪练", "Start Today's Session"), fillColor = "#FF7A59", strokeColor = "#FFD79A").apply {
                setOnClickListener { requestTrainingStart() }
            }
        actionRow.addView(
            startButtonView,
            LinearLayout.LayoutParams(dp(214), dp(58)),
        )
        dashboardColumn.addView(actionRow)
        dashboardColumn.addView(verticalSpace(12))

        dashboardColumn.addView(buildTabBar())
        dashboardColumn.addView(verticalSpace(10))

        contentContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedFill("#CC0B1B27", "#345E7A", 22)
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
        dashboardColumn.addView(
            contentContainer,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun buildTodayCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            background = roundedFill("#D9122332", "#4E7996", 24)
            setPadding(dp(18), dp(16), dp(18), dp(16))

            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#9FE6FF"))
                    textSize = 12f
                    text = text("今日课程", "TODAY'S COURSE")
                },
            )
            todayDayView =
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                }
            addView(todayDayView)
            todayThemeView =
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#FFE6BF"))
                    textSize = 17f
                    typeface = Typeface.DEFAULT_BOLD
                }
            addView(todayThemeView)
            todayMetaView =
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#D9ECF7"))
                    textSize = 13f
                    setPadding(0, dp(8), 0, 0)
                }
            addView(todayMetaView)
            todayLoadView =
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#FFCE7A"))
                    textSize = 13f
                    setPadding(0, dp(8), 0, 0)
                }
            addView(todayLoadView)
        }

    private fun buildSummaryCard(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            background = roundedFill("#D90E1821", "#45667E", 24)
            setPadding(dp(16), dp(14), dp(16), dp(14))

            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#9FE6FF"))
                    textSize = 12f
                    text = text("训练概览", "PROGRESS")
                },
            )

            addView(verticalSpace(8))
            addView(summarySectionLabel(text("当日", "Today")))
            addView(verticalSpace(8))
            addView(
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    val todayHitsTile = summaryStatTile(text("击中", "Hits"))
                    weekValueView = todayHitsTile.second
                    addView(todayHitsTile.first, LinearLayout.LayoutParams(0, dp(72), 1f))
                    addView(horizontalSpace(8))

                    val todayCaloriesTile = summaryStatTile(text("消耗", "Burn"))
                    monthValueView = todayCaloriesTile.second
                    addView(todayCaloriesTile.first, LinearLayout.LayoutParams(0, dp(72), 1f))
                    addView(horizontalSpace(8))

                    val todayFatTile = summaryStatTile(text("燃脂", "Fat"))
                    trendValueView = todayFatTile.second
                    addView(todayFatTile.first, LinearLayout.LayoutParams(0, dp(72), 1f))
                },
            )

            addView(verticalSpace(10))
            addView(summarySectionLabel(text("累计", "Total")))
            addView(verticalSpace(8))
            addView(
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL

                    val streakTile = summaryStatTile(text("打卡", "Streak"))
                    streakValueView = streakTile.second
                    addView(streakTile.first, LinearLayout.LayoutParams(0, dp(72), 1f))
                    addView(horizontalSpace(8))

                    val totalCaloriesTile = summaryStatTile(text("消耗", "Burn"))
                    cumulativeCaloriesValueView = totalCaloriesTile.second
                    addView(totalCaloriesTile.first, LinearLayout.LayoutParams(0, dp(72), 1f))
                    addView(horizontalSpace(8))

                    val totalFatTile = summaryStatTile(text("燃脂", "Fat"))
                    cumulativeFatBurnValueView = totalFatTile.second
                    addView(totalFatTile.first, LinearLayout.LayoutParams(0, dp(72), 1f))
                },
            )
        }

    private fun summarySectionLabel(label: String): TextView =
        TextView(this).apply {
            setTextColor(Color.parseColor("#8FD8F5"))
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
            text = label
        }

    private fun summaryStatTile(label: String): Pair<View, TextView> {
        val valueView =
            TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                setLineSpacing(0f, 0.92f)
                text = "--"
            }
        val tile =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = roundedFill("#112A3A", "#2F5268", 18)
                setPadding(dp(8), dp(7), dp(8), dp(7))
                addView(
                    TextView(this@FatBurnCoachActivity).apply {
                        setTextColor(Color.parseColor("#9FD7EC"))
                        textSize = 10.5f
                        gravity = Gravity.CENTER_HORIZONTAL
                        text = label
                    },
                )
                addView(valueView)
            }
        return tile to valueView
    }

    private fun summaryMetricText(value: String, unit: String? = null): CharSequence {
        if (unit.isNullOrBlank()) {
            return value
        }
        val content = "$value\n$unit"
        return SpannableString(content).apply {
            val unitStart = value.length + 1
            setSpan(RelativeSizeSpan(0.58f), unitStart, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(Color.parseColor("#9FD7EC")), unitStart, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(StyleSpan(Typeface.NORMAL), unitStart, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun buildTabBar(): View =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    listOf(
                        DashboardTab.Plan to text("教练计划", "Coach Plan"),
                        DashboardTab.Report to text("趋势报告", "Reports"),
                        DashboardTab.Achievement to text("坚持成就", "Milestones"),
                    ).forEachIndexed { index, (tab, label) ->
                        val chip =
                            TextView(this@FatBurnCoachActivity).apply {
                                minWidth = dp(112)
                                minHeight = dp(42)
                                minimumHeight = dp(42)
                                gravity = Gravity.CENTER
                                text = label
                                textSize = 13f
                                setPadding(dp(18), dp(12), dp(18), dp(12))
                                isClickable = true
                                isFocusable = true
                                setOnClickListener {
                                    selectedTab = tab
                                    refreshTabButtons()
                                    rebuildTabContent()
                                    resetDashboardScrollPosition()
                                }
                            }
                        tabButtons[tab] = chip
                        addView(
                            chip,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                dp(44),
                            ),
                        )
                        if (index < 2) {
                            addView(horizontalSpace(8))
                        }
                    }
                },
            )
        }

    private fun buildStatusBar(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedFill("#D91B2A39", "#5FB7D8", 22)
            setPadding(dp(16), dp(9), dp(16), dp(9))

            val labelsRow =
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
            val valuesRow =
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(0, dp(3), 0, 0)
                }

            fun addMetric(label: String, bindValue: (TextView) -> Unit) {
                labelsRow.addView(
                    TextView(this@FatBurnCoachActivity).apply {
                        setTextColor(Color.parseColor("#9AD5EA"))
                        textSize = 10.5f
                        gravity = Gravity.CENTER
                        text = label
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
                valuesRow.addView(
                    TextView(this@FatBurnCoachActivity).apply {
                        setTextColor(Color.WHITE)
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        text = "--"
                        bindValue(this)
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                )
            }

            addMetric(text("击中", "Hits")) { totalHitsValueView = it }
            addMetric(text("卡路里", "Calories")) { caloriesValueView = it }
            addMetric(text("燃脂", "Fat Burn")) { fatBurnValueView = it }
            addMetric(text("倒计时", "Timer")) { timerValueView = it }

            addView(labelsRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(valuesRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun buildResultPopup(): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            background = roundedFill("#F20E1A24", "#74D4FF", 24)
            setPadding(dp(22), dp(22), dp(22), dp(20))

            resultTitleView =
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 24f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
            addView(resultTitleView)

            resultBodyView =
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#E6F4FF"))
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, dp(14), 0, dp(16))
                }
            addView(resultBodyView)

            val buttons =
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
            resultPrimaryButtonView =
                commandButton(text("查看趋势", "Open Report"), fillColor = "#FF6B4A", strokeColor = "#FFD67B").apply {
                    setOnClickListener {
                        hideTrainingSummaryPopup()
                        selectedTab = DashboardTab.Report
                        refreshTabButtons()
                        refreshDashboard()
                        resetDashboardScrollPosition()
                        renderIdleState()
                    }
                }
            buttons.addView(
                resultPrimaryButtonView,
                LinearLayout.LayoutParams(dp(156), dp(48)),
            )
            buttons.addView(horizontalSpace(10))
            resultSecondaryButtonView =
                commandButton(text("返回陪练", "Back Home"), fillColor = "#0D2C3F", strokeColor = "#73CAF2").apply {
                    setOnClickListener {
                        hideTrainingSummaryPopup()
                        selectedTab = DashboardTab.Plan
                        refreshTabButtons()
                        refreshDashboard()
                        resetDashboardScrollPosition()
                        renderIdleState()
                    }
                }
            buttons.addView(
                resultSecondaryButtonView,
                LinearLayout.LayoutParams(dp(156), dp(48)),
            )
            addView(buttons)
        }

    private fun buildBackButton(): View =
        TextView(this).apply {
            text = "←"
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(Color.WHITE)
            background = roundedFill("#B3122432", "#66C8F5", 20)
            setOnClickListener { finish() }
        }

    private fun refreshDashboard() {
        val reports = loadReports()
        currentAdvice = buildCoachAdvice(reports)
        currentPlan = todayPlan(reports)
        gameView.setDifficultyPreset(currentPlan.toBlitzDifficultyPreset())
        refreshHomeCards()
        refreshTabButtons()
        rebuildTabContent()
        updateStartButton()
    }

    private fun refreshHomeCards() {
        val reports = loadReports()
        val todayReport = reports.firstOrNull { it.dayIndex == currentPlan.dayIndex }
        val streak = currentStreakDays(reports)
        val accepted = isTodayPlanAccepted()
        val todayHits = todayReport?.totalHits ?: 0
        val todayCalories = todayReport?.calories ?: 0f
        val todayFatBurn = todayReport?.fatBurnGrams ?: 0f
        val totalCalories = reports.sumOf { it.calories.toDouble() }.toFloat()
        val totalFatBurn = reports.sumOf { it.fatBurnGrams.toDouble() }.toFloat()

        titleView.text = text("燃脂陪练", "Fat Burn Coach")
        subtitleView.text = text("AI 教练根据近 1 天 / 7 天 / 30 天训练情况生成今日减脂计划", "AI coach builds today's fat-loss plan from your recent day, week, and month data.")

        todayDayView.text =
            text(
                "陪练第${currentPlan.dayIndex}天 · ${if (accepted) "已采纳计划" else "待确认建议"}",
                "Coach Day ${currentPlan.dayIndex} · ${if (accepted) "Plan accepted" else "Advice pending"}",
                "Jour coach ${currentPlan.dayIndex} · ${if (accepted) "Plan accepté" else "Conseil en attente"}",
                "โค้ชเดย์ ${currentPlan.dayIndex} · ${if (accepted) "ยืนยันแผนแล้ว" else "รอยืนยันคำแนะนำ"}",
            )
        todayThemeView.text = currentPlan.title(languageCode)
        todayMetaView.text =
            text(
                "${currentAdvice.summary(languageCode)}\n${currentPlan.focus(languageCode)} · ${formatDuration(currentPlan.totalDurationSec)} · ${currentPlan.roundCount}轮",
                "${currentAdvice.summary(languageCode)}\n${currentPlan.focus(languageCode)} · ${formatDuration(currentPlan.totalDurationSec)} · ${currentPlan.roundCount} rounds",
                "${currentAdvice.summary(languageCode)}\n${currentPlan.focus(languageCode)} · ${formatDuration(currentPlan.totalDurationSec)} · ${currentPlan.roundCount} manches",
                "${currentAdvice.summary(languageCode)}\n${currentPlan.focus(languageCode)} · ${formatDuration(currentPlan.totalDurationSec)} · ${currentPlan.roundCount} รอบ",
            )
        todayLoadView.text =
            text(
                "${currentPlan.intensity(languageCode)} · 训练节奏 ${currentPlan.bpm} BPM · 目标 ${currentPlan.targetValidHits} 击 · 预计 ${formatDecimal(currentPlan.estimatedCalories, 0)} kcal",
                "${currentPlan.intensity(languageCode)} · Tempo ${currentPlan.bpm} BPM · Target ${currentPlan.targetValidHits} hits · Est ${formatDecimal(currentPlan.estimatedCalories, 0)} kcal",
                "${currentPlan.intensity(languageCode)} · Tempo ${currentPlan.bpm} BPM · Objectif ${currentPlan.targetValidHits} coups · Est ${formatDecimal(currentPlan.estimatedCalories, 0)} kcal",
                "${currentPlan.intensity(languageCode)} · จังหวะ ${currentPlan.bpm} BPM · เป้าหมาย ${currentPlan.targetValidHits} ครั้ง · คาด ${formatDecimal(currentPlan.estimatedCalories, 0)} kcal",
            )

        streakValueView.text = summaryMetricText(streak.toString(), text("天", "d"))
        weekValueView.text = todayHits.toString()
        monthValueView.text = summaryMetricText(formatDecimal(todayCalories, 0), "kcal")
        trendValueView.text = summaryMetricText(formatDecimal(todayFatBurn, 1), "g")
        cumulativeCaloriesValueView.text = summaryMetricText(formatDecimal(totalCalories, 0), "kcal")
        cumulativeFatBurnValueView.text = summaryMetricText(formatDecimal(totalFatBurn, 1), "g")
    }

    private fun refreshTabButtons() {
        tabButtons.forEach { (tab, chip) ->
            val selected = selectedTab == tab
            chip.setTextColor(Color.parseColor(if (selected) "#081018" else "#D9EEF8"))
            chip.typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            chip.background =
                if (selected) {
                    roundedFill("#FFD88E", "#FFF3D0", 18)
                } else {
                    roundedFill("#18273A", "#34536B", 18)
                }
        }
    }

    private fun rebuildTabContent() {
        contentContainer.removeAllViews()
        when (selectedTab) {
            DashboardTab.Plan -> buildPlanTab()
            DashboardTab.Report -> buildReportTab()
            DashboardTab.Achievement -> buildAchievementTab()
        }
    }

    private fun buildPlanTab() {
        val accepted = isTodayPlanAccepted()
        val bodyMetrics = loadBodyMetrics()
        contentContainer.addView(
            sectionHeader(
                text("AI 教练建议", "AI Coach Recommendation"),
                text("系统根据今日、近 7 天、近 30 天训练表现给出减脂参考意见。", "The coach uses your daily, weekly, and monthly load to shape today's recommendation."),
            ),
        )
        contentContainer.addView(verticalSpace(10))
        contentContainer.addView(coachAdviceCard(currentAdvice, currentPlan))
        contentContainer.addView(verticalSpace(12))
        contentContainer.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL

                addView(
                    commandButton(text("采纳建议", "Accept Advice"), fillColor = "#FF7A59", strokeColor = "#FFD79A").apply {
                        isEnabled = !accepted
                        alpha = if (accepted) 0.55f else 1f
                        setOnClickListener { acceptTodayAdvice() }
                    },
                    LinearLayout.LayoutParams(0, dp(48), 1f),
                )
                addView(horizontalSpace(10))
                addView(
                    commandButton(text("重新评估", "Re-evaluate"), fillColor = "#163446", strokeColor = "#76D9FF").apply {
                        setOnClickListener { regenerateAdvice() }
                    },
                    LinearLayout.LayoutParams(0, dp(48), 1f),
                )
            },
        )
        contentContainer.addView(verticalSpace(16))

        contentContainer.addView(
            sectionHeader(
                text("身体数据", "Body Metrics"),
                text("记录今日体重与腰围，教练会把体型变化一起纳入陪练建议。", "Log today's weight and waist so the coach can adjust the plan with body-shape progress."),
            ),
        )
        contentContainer.addView(verticalSpace(10))
        contentContainer.addView(bodyMetricsCard())
        contentContainer.addView(verticalSpace(16))

        contentContainer.addView(
            sectionHeader(
                text("今日陪练计划", "Today's Guided Plan"),
                text("接受建议后即可开始训练；计划会保存在本机，并在训练后继续生成后续参考。", "Accept the coach's advice to lock today's plan and continue the coaching loop after the session."),
            ),
        )
        contentContainer.addView(verticalSpace(10))
        contentContainer.addView(
            planRow(
                plan = currentPlan,
                status = if (accepted) text("已采纳", "Accepted") else text("建议预览", "Preview"),
                highlight = true,
            ),
        )
        contentContainer.addView(verticalSpace(12))
        contentContainer.addView(
            sectionHeader(
                text("制定依据", "Plan Basis"),
                text("把训练负荷拆开给你看，方便理解今天为什么这样练。", "A transparent breakdown of why today's load looks the way it does."),
            ),
        )
        contentContainer.addView(verticalSpace(10))
        val reports = loadReports()
        val weekAggregate =
            aggregateWindow(
                reports.filter { sameWeek(it.timestampMs, System.currentTimeMillis()) },
                bodyMetrics.filter { sameWeek(it.timestampMs, System.currentTimeMillis()) },
            )
        val monthAggregate =
            aggregateWindow(
                reports.filter { sameMonth(it.timestampMs, System.currentTimeMillis()) },
                bodyMetrics.filter { sameMonth(it.timestampMs, System.currentTimeMillis()) },
            )
        listOf(
            aggregateCard(
                text("今日", "Today"),
                loadReportForDay(currentPlan.dayIndex)?.let {
                    aggregateWindow(
                        listOf(it),
                        bodyMetrics.filter { sameDay(it.timestampMs, System.currentTimeMillis()) },
                    )
                } ?: aggregateWindow(emptyList(), bodyMetrics.filter { sameDay(it.timestampMs, System.currentTimeMillis()) }),
                if (accepted) text("已锁定", "Locked") else text("待开始", "Pending"),
            ),
            aggregateCard(text("本周", "This Week"), weekAggregate, highlight = "${reports.count { sameWeek(it.timestampMs, System.currentTimeMillis()) }} ${text("天", "days")}"),
            aggregateCard(text("本月", "This Month"), monthAggregate, highlight = "${reports.count { sameMonth(it.timestampMs, System.currentTimeMillis()) }} ${text("次", "runs")}"),
        ).forEachIndexed { index, view ->
            contentContainer.addView(view)
            if (index < 2) {
                contentContainer.addView(verticalSpace(10))
            }
        }
    }

    private fun buildReportTab() {
        val reports = loadReports()
        val bodyMetrics = loadBodyMetrics()
        val todayReport = loadReportForDay(currentPlan.dayIndex)
        val dayAggregate = aggregateWindow(todayReport?.let { listOf(it) } ?: emptyList(), bodyMetrics.filter { sameDay(it.timestampMs, System.currentTimeMillis()) })
        val weekAggregate = aggregateWindow(reports.filter { sameWeek(it.timestampMs, System.currentTimeMillis()) }, bodyMetrics.filter { sameWeek(it.timestampMs, System.currentTimeMillis()) })
        val monthAggregate = aggregateWindow(reports.filter { sameMonth(it.timestampMs, System.currentTimeMillis()) }, bodyMetrics.filter { sameMonth(it.timestampMs, System.currentTimeMillis()) })

        contentContainer.addView(sectionHeader(text("日 / 周 / 月汇总", "Daily / Weekly / Monthly Summary"), text("卡路里、燃脂量与体重趋势都会沉淀到本地报告。", "Calories, fat burn, and weight trend are stored in local reports.")))
        contentContainer.addView(verticalSpace(12))

        contentContainer.addView(aggregateCard(text("今日", "Today"), dayAggregate, highlight = todayReport?.grade ?: "--"))
        contentContainer.addView(verticalSpace(10))
        contentContainer.addView(aggregateCard(text("本周", "This Week"), weekAggregate, highlight = "${reports.count { sameWeek(it.timestampMs, System.currentTimeMillis()) }} ${text("天", "days")}"))
        contentContainer.addView(verticalSpace(10))
        contentContainer.addView(aggregateCard(text("本月", "This Month"), monthAggregate, highlight = "${reports.count { sameMonth(it.timestampMs, System.currentTimeMillis()) }} / 30"))

        contentContainer.addView(verticalSpace(14))
        contentContainer.addView(sectionHeader(text("最近 7 天趋势", "Recent 7-Day Trend"), text("观察日课总击中、卡路里与燃脂量的连续变化。", "Track recent hits, calories, and fat-burn trends.")))
        contentContainer.addView(verticalSpace(10))
        buildRecentTrendRows(reports).forEachIndexed { index, view ->
            contentContainer.addView(view)
            if (index < 6) {
                contentContainer.addView(verticalSpace(8))
            }
        }
    }

    private fun buildAchievementTab() {
        val milestones = buildAchievements(loadReports())
        contentContainer.addView(sectionHeader(text("里程碑成就", "Milestones"), text("把减脂目标拆成一连串可感知的小胜利。", "Turn long-term fat loss into visible small wins.")))
        contentContainer.addView(verticalSpace(12))
        milestones.forEachIndexed { index, milestone ->
            contentContainer.addView(achievementCard(milestone))
            if (index < milestones.lastIndex) {
                contentContainer.addView(verticalSpace(10))
            }
        }
    }

    private fun sectionHeader(
        title: String,
        subtitle: String,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    text = title
                },
            )
            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#AFD0E0"))
                    textSize = 12.5f
                    setPadding(0, dp(4), 0, 0)
                    text = subtitle
                },
            )
        }

    private fun metricPill(
        label: String,
        color: String,
    ): View =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#0B1218"))
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = roundedFill(color, color, 16)
        }

    private fun coachAdviceCard(
        advice: CoachAdvice,
        plan: ChallengeDayPlan,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#182A37", "#5ECFFF", 18)
            setPadding(dp(16), dp(16), dp(16), dp(16))

            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#7CD7FF"))
                    textSize = 11.5f
                    typeface = Typeface.DEFAULT_BOLD
                    text = text("教练建议", "COACH NOTE")
                },
            )
            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(6), 0, 0)
                    text = advice.summary(languageCode)
                },
            )
            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#D2EAF7"))
                    textSize = 13.5f
                    setPadding(0, dp(10), 0, 0)
                    text = advice.reason(languageCode)
                },
            )
            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#FFD893"))
                    textSize = 12.5f
                    setPadding(0, dp(10), 0, 0)
                    text = advice.coachCue(languageCode)
                },
            )
            addView(verticalSpace(12))
            addView(
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(metricPill(plan.intensity(languageCode), "#9FE6FF"))
                    addView(horizontalSpace(8))
                    addView(metricPill(formatDuration(plan.totalDurationSec), "#FFD88E"))
                    addView(horizontalSpace(8))
                    addView(metricPill(text("目标 ${plan.targetValidHits} 击", "Target ${plan.targetValidHits}"), "#FFB35A"))
                },
            )
        }

    private fun bodyMetricsCard(): View {
        val metrics = loadBodyMetrics()
        val latestMetric = metrics.maxByOrNull { it.timestampMs }
        val baselineMetric = metrics.minByOrNull { it.timestampMs }
        val weightField = metricInputField(latestMetric?.weightKg ?: DEFAULT_BASE_WEIGHT_KG, text("体重 kg", "Weight kg"))
        val waistField = metricInputField(latestMetric?.waistCm ?: DEFAULT_BASE_WAIST_CM, text("腰围 cm", "Waist cm"))
        val summaryView =
            TextView(this).apply {
                setTextColor(Color.parseColor("#D2EAF7"))
                textSize = 12.5f
                text =
                    if (latestMetric == null || baselineMetric == null) {
                        text("还没有身体记录。建议先录入今天的体重和腰围，后面会更容易看出体型变化。", "No body metrics yet. Record today's weight and waist to make trend changes clearer.")
                    } else {
                        text(
                            "最近记录 ${formatMetricDate(latestMetric.timestampMs)} · 体重变化 ${signedDelta(baselineMetric.weightKg - latestMetric.weightKg)} kg · 腰围改善 ${formatDecimal((baselineMetric.waistCm - latestMetric.waistCm).coerceAtLeast(0f), 1)} cm",
                            "Latest ${formatMetricDate(latestMetric.timestampMs)} · Weight delta ${signedDelta(baselineMetric.weightKg - latestMetric.weightKg)} kg · Waist change ${formatDecimal((baselineMetric.waistCm - latestMetric.waistCm).coerceAtLeast(0f), 1)} cm",
                        )
                    }
            }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#122330", "#35556E", 18)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            addView(
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        weightField,
                        LinearLayout.LayoutParams(0, dp(44), 1f),
                    )
                    addView(horizontalSpace(10))
                    addView(
                        waistField,
                        LinearLayout.LayoutParams(0, dp(44), 1f),
                    )
                },
            )
            addView(verticalSpace(10))
            addView(
                commandButton(text("保存身体数据", "Save Metrics"), fillColor = "#163446", strokeColor = "#76D9FF").apply {
                    setOnClickListener {
                        val weight = weightField.text.toString().toFloatOrNull()
                        val waist = waistField.text.toString().toFloatOrNull()
                        when {
                            weight == null || weight !in 30f..200f ->
                                Toast.makeText(
                                    this@FatBurnCoachActivity,
                                    text("请输入有效体重，建议范围 30-200 kg。", "Enter a valid weight between 30 and 200 kg."),
                                    Toast.LENGTH_SHORT,
                                ).show()

                            waist == null || waist !in 40f..180f ->
                                Toast.makeText(
                                    this@FatBurnCoachActivity,
                                    text("请输入有效腰围，建议范围 40-180 cm。", "Enter a valid waist between 40 and 180 cm."),
                                    Toast.LENGTH_SHORT,
                                ).show()

                            else -> {
                                saveTodayBodyMetrics(weight, waist)
                                Toast.makeText(
                                    this@FatBurnCoachActivity,
                                    text("身体数据已保存，教练会按新数据更新建议。", "Body metrics saved. The coach will update guidance with the new data."),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                refreshDashboard()
                            }
                        }
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)),
            )
            addView(verticalSpace(10))
            addView(summaryView)
        }
    }

    private fun metricInputField(
        initialValue: Float,
        hintLabel: String,
    ): EditText =
        EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#7FA3B8"))
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            gravity = Gravity.CENTER
            setSingleLine()
            hint = hintLabel
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedFill("#0E1B26", "#45667E", 16)
            setText(formatDecimal(initialValue, 1))
        }

    private fun planRow(
        plan: ChallengeDayPlan,
        status: String,
        highlight: Boolean,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill(if (highlight) "#1C3241" else "#131E28", if (highlight) "#8EE6FF" else "#304655", 18)
            setPadding(dp(14), dp(12), dp(14), dp(12))

            val topRow =
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(this@FatBurnCoachActivity).apply {
                            setTextColor(Color.WHITE)
                            textSize = 15f
                            typeface = Typeface.DEFAULT_BOLD
                            text = text("第${plan.dayIndex}天 · ${plan.title(languageCode)}", "Day ${plan.dayIndex} · ${plan.title(languageCode)}")
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(metricPill(status, if (highlight) "#FFD68A" else "#66C7F2"))
                }
            addView(topRow)

            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#CAE8F8"))
                    textSize = 12.5f
                    setPadding(0, dp(8), 0, 0)
                        text =
                            text(
                                "${plan.phaseLabel(languageCode)} · ${plan.roundCount}轮 · ${plan.workSec}秒冲刺 / ${plan.restSec}秒恢复 · ${plan.bpm} BPM",
                                "${plan.phaseLabel(languageCode)} · ${plan.roundCount} rounds · ${plan.workSec}s work / ${plan.restSec}s recover · ${plan.bpm} BPM",
                            )
                },
            )
            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#F5C46D"))
                    textSize = 12f
                    setPadding(0, dp(6), 0, 0)
                        text =
                            text(
                                "${plan.focus(languageCode)} · ${formatDuration(plan.totalDurationSec)} · 负荷 ${plan.loadScore}/10 · ${formatDecimal(plan.estimatedCalories, 0)} kcal",
                                "${plan.focus(languageCode)} · ${formatDuration(plan.totalDurationSec)} · Load ${plan.loadScore}/10 · ${formatDecimal(plan.estimatedCalories, 0)} kcal",
                            )
                },
            )
        }

    private fun aggregateCard(
        title: String,
        aggregate: AggregateWindow,
        highlight: String,
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill("#122330", "#35556E", 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))

            val topRow =
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(this@FatBurnCoachActivity).apply {
                            setTextColor(Color.WHITE)
                            textSize = 16f
                            typeface = Typeface.DEFAULT_BOLD
                            text = title
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(metricPill(highlight, "#FFB35A"))
                }
            addView(topRow)
            addView(verticalSpace(8))
            addView(aggregateInfoRow(text("完成天数", "Completed"), "${aggregate.completedDays}", "#FFFFFF"))
            addView(aggregateInfoRow(text("消耗", "Calories"), "${formatDecimal(aggregate.calories, 0)} kcal", "#FFCF7A"))
            addView(aggregateInfoRow(text("燃脂", "Fat burn"), "${formatDecimal(aggregate.fatBurnGrams, 1)} g", "#FF8D8D"))
            addView(aggregateInfoRow(text("平均击中", "Avg hits"), formatDecimal(aggregate.avgHits, 0), "#9FE6FF"))
            addView(aggregateInfoRow(text("平均消耗", "Avg kcal"), "${formatDecimal(aggregate.avgCaloriesPerSession, 0)} kcal", "#D6F4FF"))
            addView(aggregateInfoRow(text("当前体重", "Weight"), "${formatDecimal(aggregate.estimatedWeightKg, 1)} kg", "#F7F8FF"))
            addView(aggregateInfoRow(text("体重变化", "Weight delta"), "${signedDelta(aggregate.weightDeltaKg)} kg", "#FFD9A6"))
            addView(aggregateInfoRow(text("当前腰围", "Waist"), "${formatDecimal(aggregate.currentWaistCm, 1)} cm", "#D6F4FF"))
            addView(aggregateInfoRow(text("腰围改善", "Waist delta"), "${formatDecimal(aggregate.estimatedWaistDeltaCm, 1)} cm", "#8AF0AE"))
        }

    private fun aggregateInfoRow(
        label: String,
        value: String,
        valueColor: String = "#FFFFFF",
    ): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#9DD2E8"))
                    textSize = 12f
                    text = label
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor(valueColor))
                    textSize = 12.5f
                    typeface = Typeface.DEFAULT_BOLD
                    text = value
                },
            )
        }

    private fun buildRecentTrendRows(reports: List<DayReport>): List<View> {
        val rows = ArrayList<View>()
        val today = Calendar.getInstance()
        repeat(7) { index ->
            val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(6 - index)) }
            val report = reports.firstOrNull { sameDay(it.timestampMs, day.timeInMillis) }
            rows +=
                LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = roundedFill("#10202B", "#274052", 16)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    addView(
                        TextView(this@FatBurnCoachActivity).apply {
                            setTextColor(Color.parseColor("#D7ECF7"))
                            textSize = 12f
                            text = SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(day.timeInMillis))
                        },
                        LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT),
                    )
                    addView(
                        View(this@FatBurnCoachActivity).apply {
                            background = roundedFill("#2B465A", "#2B465A", 6)
                        },
                        LinearLayout.LayoutParams(dp(1), dp(22)),
                    )
                    addView(horizontalSpace(10))
                    addView(
                        TextView(this@FatBurnCoachActivity).apply {
                            setTextColor(Color.WHITE)
                            textSize = 12f
                            text =
                                if (report == null) {
                                    text("未打卡", "Rest")
                                } else {
                                    "${report.totalHits}${text(" 击", " hits")} · ${formatDecimal(report.calories, 0)} kcal · ${formatDecimal(report.fatBurnGrams, 1)} g"
                                }
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(metricPill(if (sameDay(day.timeInMillis, today.timeInMillis)) text("今日", "Today") else text("记录", "Saved"), if (report == null) "#5D7587" else "#6BD4FF"))
                }
        }
        return rows
    }

    private fun achievementCard(milestone: AchievementMilestone): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill(if (milestone.unlocked) "#152B1E" else "#131E28", if (milestone.unlocked) "#6FE59A" else "#304655", 20)
            setPadding(dp(14), dp(12), dp(14), dp(12))

            val topRow =
                LinearLayout(this@FatBurnCoachActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        TextView(this@FatBurnCoachActivity).apply {
                            setTextColor(Color.WHITE)
                            textSize = 15f
                            typeface = Typeface.DEFAULT_BOLD
                            text = milestone.title(languageCode)
                        },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                    )
                    addView(metricPill(if (milestone.unlocked) text("已解锁", "Unlocked") else milestone.progressLabel, if (milestone.unlocked) "#6FE59A" else "#FFC669"))
                }
            addView(topRow)
            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor("#CAE8F8"))
                    textSize = 12.5f
                    setPadding(0, dp(8), 0, dp(10))
                    text = milestone.description(languageCode)
                },
            )
            addView(progressBar(milestone.progress))
            addView(
                TextView(this@FatBurnCoachActivity).apply {
                    setTextColor(Color.parseColor(if (milestone.unlocked) "#A9F2C0" else "#C9E6F5"))
                    textSize = 11.5f
                    setPadding(0, dp(8), 0, 0)
                    text = achievementFooterText(milestone)
                },
            )
        }

    private fun achievementFooterText(milestone: AchievementMilestone): String =
        if (milestone.unlocked) {
            text("已纳入你的坚持记录，继续保持这个节奏。", "Unlocked and recorded. Keep the same rhythm.")
        } else {
            text("当前进度 ${milestone.progressLabel}，再推进一点就能解锁。", "Current progress ${milestone.progressLabel}. You are close to unlocking it.")
        }

    private fun progressBar(progress: Float): View =
        FrameLayout(this).apply {
            background = roundedFill("#1E3342", "#1E3342", 8)
            addView(
                View(this@FatBurnCoachActivity).apply {
                    background = roundedFill("#FF7A59", "#FFCA7A", 8)
                    layoutParams =
                        FrameLayout.LayoutParams(
                            (dp(260) * progress.coerceIn(0f, 1f)).roundToInt().coerceAtLeast(dp(8)),
                            dp(8),
                        )
                },
            )
            layoutParams =
                LinearLayout.LayoutParams(
                    dp(260),
                    dp(8),
                )
        }

    private fun updateStartButton() {
        val accepted = isTodayPlanAccepted()
        val completed = loadReportForDay(currentPlan.dayIndex) != null
        startButtonView.text =
            when {
                !accepted -> text("先采纳建议", "Accept Advice First")
                completed -> text("重做今日陪练", "Replay Today's Session")
                else -> text("开始今日陪练", "Start Today's Session")
            }
        startButtonView.alpha = if (accepted) 1f else 0.86f
    }

    private fun requestTrainingStart() {
        if (trainingActive || launchCountdownActive) {
            return
        }
        if (!isTodayPlanAccepted()) {
            selectedTab = DashboardTab.Plan
            refreshDashboard()
            return
        }
        startLaunchCountdown()
    }

    private fun startLaunchCountdown() {
        if (launchCountdownActive || trainingActive) {
            return
        }
        BoxingBleRuntime.enableGyro()
        pendingStartAfterPermission = false
        clearDetectorRecoveryState()
        launchCountdownActive = true
        hideTrainingSummaryPopup()
        lastDetectorStateType = "loading"
        dispatchDetectorState("loading", text("开始准备蓝牙击中识别。", "Preparing Bluetooth hit detection."))
        startDetectorSession()
        updateTrainingUi()

        launchCountdownJob?.cancel()
        launchCountdownJob =
            lifecycleScope.launch {
                countdownView.visibility = View.VISIBLE
                waitForDetectorReadyOrTimeout()
                val steps = listOf("3", "2", "1", "GO")
                val delays = listOf(760L, 760L, 760L, 640L)
                for (index in steps.indices) {
                    countdownView.text =
                        if (steps[index] == "GO") {
                            text("开始", "GO!", "Partez !", "เริ่ม!")
                        } else {
                            steps[index]
                        }
                    speakLaunchCountdownStep(steps[index])
                    delay(delays[index])
                }
                countdownView.visibility = View.GONE
                startTrainingSession()
            }
    }

    private suspend fun waitForDetectorReadyOrTimeout() {
        val deadline = SystemClock.elapsedRealtime() + DETECTOR_READY_WAIT_TIMEOUT_MS
        while (
            launchCountdownActive &&
            !isFinishing &&
            lastDetectorStateType != "ready" &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            countdownView.text = text("校准中", "CAL")
            delay(120L)
        }
    }

    private fun cancelLaunchCountdown(showMessage: Boolean) {
        if (launchCountdownActive) {
            BoxingBleRuntime.disableGyro()
        }
        launchCountdownJob?.cancel()
        launchCountdownJob = null
        launchCountdownActive = false
        countdownView.visibility = View.GONE
        if (showMessage) {
            dispatchDetectorState("finished", text("已取消开始倒计时。", "Launch countdown cancelled."))
        }
    }

    private fun startTrainingSession() {
        currentPlan = todayPlan()
        remainingTrainingMs = currentPlan.totalDurationSec * 1_000L
        trainingActive = true
        launchCountdownActive = false
        trainingRunId = SystemClock.elapsedRealtime()
        clearDetectorRecoveryState()
        gameView.setDifficultyPreset(currentPlan.toBlitzDifficultyPreset())
        gameView.visibility = View.VISIBLE
        gameView.beginTraining()
        if (
            bleHitListener == null ||
            lastDetectorStateType == "finished" ||
            lastDetectorStateType == "error" ||
            lastDetectorStateType == "permission_denied"
        ) {
            lastDetectorStateType = "loading"
            dispatchDetectorState("loading", text("正在准备蓝牙击中识别。", "Preparing Bluetooth hit detection."))
            startDetectorSession()
        } else {
            gameView.updateDetectorState(lastDetectorStateType)
        }
        updateOverlay(gameView.currentSnapshot())
        updateTimerDisplay()
        updateTrainingUi()
        startTrainingCountdown()
        scheduleDebugAutoFinishIfNeeded()
    }

    private fun finishTrainingSession(manual: Boolean) {
        if (!trainingActive && !launchCountdownActive) {
            return
        }
        BoxingBleRuntime.disableGyro()
        cancelLaunchCountdown(showMessage = false)
        val snapshot = gameView.currentSnapshot().toCoachOverlaySnapshot()
        val sessionDurationSeconds = currentSessionDurationSeconds()
        trainingActive = false
        pauseTrainingCountdown()
        cancelDebugAutoFinish()
        stopDetectorSession()
        clearDetectorRecoveryState()
        gameView.endTraining()
        persistDailyReport(snapshot, sessionDurationSeconds)
        maybeUploadCoachSessionToCloud(snapshot, sessionDurationSeconds)
        refreshDashboard()
        showTrainingSummaryPopup(snapshot, manual)
        updateTrainingUi()
    }

    private fun showTrainingSummaryPopup(
        snapshot: FatBurnCoachGameView.OverlaySnapshot,
        manual: Boolean,
    ) {
        val grade = evaluateGrade(snapshot, currentPlan)
        val nextAdvice = buildCoachAdvice(loadReports())
        val previousReport =
            loadReports()
                .filter { it.dayIndex < currentPlan.dayIndex }
                .maxByOrNull { it.dayIndex }

        val deltaHits = snapshot.totalHits - (previousReport?.totalHits ?: 0)
        val deltaCalories = snapshot.calories - (previousReport?.calories ?: 0f)
        val streak = currentStreakDays(loadReports())
        val body =
            StringBuilder().apply {
                appendLine(
                    text(
                        "总击中 ${snapshot.totalHits} 次 · 消耗 ${formatDecimal(snapshot.calories, 0)} kcal · 预计燃脂 ${formatDecimal(snapshot.fatBurnGrams, 1)} g",
                        "Hits ${snapshot.totalHits} · ${formatDecimal(snapshot.calories, 0)} kcal · ${formatDecimal(snapshot.fatBurnGrams, 1)} g fat burn",
                    ),
                )
                appendLine(
                    text(
                        "平均频率 PPS ${formatDecimal(snapshot.hps, 2)} · 本轮等级 $grade · 连续打卡 ${streak} 天",
                        "Average PPS ${formatDecimal(snapshot.hps, 2)} · Grade $grade · Streak $streak days",
                    ),
                )
                append(
                    text(
                        "较上一日 ${if (deltaHits >= 0) "+" else ""}$deltaHits 次 · ${if (deltaCalories >= 0f) "+" else ""}${formatDecimal(deltaCalories, 0)} kcal",
                        "vs previous day ${if (deltaHits >= 0) "+" else ""}$deltaHits hits · ${if (deltaCalories >= 0f) "+" else ""}${formatDecimal(deltaCalories, 0)} kcal",
                    ),
                )
                appendLine()
                appendLine()
                appendLine(
                    text(
                        "教练复盘：${nextAdvice.summary(languageCode)}",
                        "Coach recap: ${nextAdvice.summary(languageCode)}",
                        "Bilan du coach : ${nextAdvice.summary(languageCode)}",
                        "สรุปจากโค้ช: ${nextAdvice.summary(languageCode)}",
                    ),
                )
                append(
                    text(
                        "下一步建议：${nextAdvice.previewPlan.focus(languageCode)}",
                        "Next suggestion: ${nextAdvice.previewPlan.focus(languageCode)}",
                        "Prochaine suggestion : ${nextAdvice.previewPlan.focus(languageCode)}",
                        "คำแนะนำถัดไป: ${nextAdvice.previewPlan.focus(languageCode)}",
                    ),
                )
                if (manual) {
                    appendLine()
                    appendLine()
                    append(text("本次为手动结束，训练报告已保存。", "This run ended manually and the report was saved."))
                }
            }.toString()

        resultTitleView.text =
            when (grade) {
                "S" -> text("教练评级 S", "Coach Grade S")
                "A" -> text("教练评级 A", "Coach Grade A")
                "B" -> text("教练评级 B", "Coach Grade B")
                else -> text("教练继续陪你练", "Coach Keeps You Going")
            }
        resultBodyView.text = body
        resultPopupView.visibility = View.VISIBLE
    }

    private fun hideTrainingSummaryPopup() {
        resultPopupView.visibility = View.GONE
    }

    private fun startTrainingCountdown() {
        countdownJob?.cancel()
        val sessionId = trainingRunId
        val totalMs = currentPlan.totalDurationSec * 1_000L
        countdownJob =
            lifecycleScope.launch {
                val startElapsedMs = SystemClock.elapsedRealtime()
                while (trainingActive && sessionId == trainingRunId) {
                    val elapsed = SystemClock.elapsedRealtime() - startElapsedMs
                    remainingTrainingMs = (totalMs - elapsed).coerceAtLeast(0L)
                    updateTimerDisplay()
                    if (remainingTrainingMs <= 0L) {
                        finishTrainingSession(manual = false)
                        break
                    }
                    delay(100L)
                }
            }
    }

    private fun pauseTrainingCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun resumeTrainingIfNeeded() {
        if (!trainingActive) {
            return
        }
        startDetectorSession()
        startTrainingCountdown()
        scheduleDebugAutoFinishIfNeeded()
    }

    private fun startDetectorSession() {
        if (!trainingActive) {
            return
        }
        stopDetectorSession()
        val listener = BoxingBleRuntime.HitListener { packet ->
            runOnUiThread {
                if (trainingActive) {
                    repeat(packet.hitDelta.coerceIn(1, 12)) {
                        gameView.registerPunch(1f, packet.hand)
                    }
                }
            }
        }
        val previewListener = BoxingBleRuntime.ImpactPreviewListener { packet ->
            runOnUiThread {
                if (trainingActive) {
                    gameView.previewPunch(packet.hand)
                }
            }
        }
        bleHitListener = listener
        bleImpactPreviewListener = previewListener
        BoxingBleRuntime.addHitListener(listener)
        BoxingBleRuntime.addImpactPreviewListener(previewListener)
        dispatchBleDetectorReady()
    }
    private fun stopDetectorSession() {
        bleHitListener?.let { BoxingBleRuntime.removeHitListener(it) }
        bleHitListener = null
        bleImpactPreviewListener?.let { BoxingBleRuntime.removeImpactPreviewListener(it) }
        bleImpactPreviewListener = null
        detectorJob?.cancel()
        detectorJob = null
    }

    private fun initSpeechEngine() {
        speechEngine =
            TextToSpeech(applicationContext) { status ->
                speechReady = status == TextToSpeech.SUCCESS
                if (speechReady) {
                    speechEngine?.language = fatBurnCoachLocale(languageCode)
                    speechEngine?.setSpeechRate(1.04f)
                    speechEngine?.setPitch(1.0f)
                }
            }
    }

    private fun speakLaunchCountdownStep(step: String) {
        if (!speechReady) {
            return
        }
        val speechText =
            when {
                languageCode.startsWith("zh") ->
                    when (step) {
                        "校准" -> "校准"
                        "3" -> "三"
                        "2" -> "二"
                        "1" -> "一"
                        else -> "开始"
                    }
                languageCode.startsWith("fr") ->
                    when (step) {
                        "校准" -> "Calibrage"
                        "GO" -> "Partez"
                        else -> step
                    }
                languageCode.startsWith("th") ->
                    when (step) {
                        "校准" -> "ปรับเทียบ"
                        "GO" -> "เริ่ม"
                        else -> step
                    }
                else -> if (step == "GO") "Go" else step
            }
        speechEngine?.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "fat_burn_countdown_$step")
    }

    private fun clearDetectorRecoveryState() {
        detectorRecoveryAttempts = 0
        detectorRecoveryJob?.cancel()
        detectorRecoveryJob = null
    }

    private fun scheduleDetectorRecovery(throwable: Throwable) {
        if (!trainingActive && !launchCountdownActive) {
            return
        }
        if (isFinishing) {
            dispatchDetectorState("error", text("当前无法使用蓝牙设备，请稍后重试。", "Bluetooth hit detection is unavailable right now."))
            return
        }
        if (detectorRecoveryAttempts >= MAX_DETECTOR_RECOVERY_ATTEMPTS) {
            dispatchDetectorState("error", text("蓝牙击中识别中断，请重新开始课程。", "Bluetooth hit detection stopped. Please restart the class."))
            return
        }
        detectorRecoveryAttempts += 1
        val recoveryAttempt = detectorRecoveryAttempts
        val recoveryRunId = trainingRunId
        dispatchDetectorState("error", text("正在恢复蓝牙击中识别…", "Recovering Bluetooth hit detection..."))
        detectorRecoveryJob?.cancel()
        detectorRecoveryJob =
            lifecycleScope.launch {
                delay((450L + detectorRecoveryAttempts * 250L).coerceAtMost(1_600L))
                if ((trainingActive || launchCountdownActive) && recoveryRunId == trainingRunId && !isFinishing) {
                    dispatchDetectorState(
                        "loading",
                        text(
                            "蓝牙击中识别短暂中断，正在重连（$recoveryAttempt/$MAX_DETECTOR_RECOVERY_ATTEMPTS）。",
                            "Bluetooth hit detection paused briefly. Reconnecting ($recoveryAttempt/$MAX_DETECTOR_RECOVERY_ATTEMPTS).",
                        ),
                    )
                    startDetectorSession()
                }
            }
    }

    private fun scheduleDebugAutoFinishIfNeeded() {
        cancelDebugAutoFinish()
        if (autoFinishAfterMsForTest <= 0L) {
            return
        }
        debugAutoFinishJob =
            lifecycleScope.launch {
                delay(autoFinishAfterMsForTest)
                if (trainingActive && !isFinishing) {
                    finishTrainingSession(manual = false)
                }
            }
    }

    private fun cancelDebugAutoFinish() {
        debugAutoFinishJob?.cancel()
        debugAutoFinishJob = null
    }

    private fun dispatchBleDetectorReady() {
        dispatchDetectorState(type = "ready", message = text("蓝牙击中识别已就绪。", "Bluetooth hit detection is ready."))
    }
    private fun dispatchDetectorState(
        type: String,
        message: String,
    ) {
        lastDetectorStateType = type
        gameView.updateDetectorState(type)
        hintView.text = message
        hintView.visibility = if (trainingActive || launchCountdownActive) View.VISIBLE else View.GONE
    }

    private fun renderIdleState() {
        trainingActive = false
        launchCountdownActive = false
        updateTrainingUi()
        cancelDebugAutoFinish()
        gameView.endTraining()
        hintView.text = text("查看今日课程后即可开始。", "Review today's class and start when ready.")
        refreshDashboard()
        resetDashboardScrollPosition()
    }

    private fun applyAutomationLaunchOptions() {
        if (
            !autoAcceptPlanForTest &&
            !autoStartSessionForTest &&
            autoSeedWeightKgForTest <= 0f &&
            autoSeedWaistCmForTest <= 0f &&
            autoOpenTabForTest.isBlank()
        ) {
            return
        }
        rootContainer.post {
            if (autoSeedWeightKgForTest > 0f && autoSeedWaistCmForTest > 0f) {
                saveTodayBodyMetrics(autoSeedWeightKgForTest, autoSeedWaistCmForTest)
                refreshDashboard()
            }
            when (autoOpenTabForTest.lowercase(Locale.US)) {
                "report" -> {
                    selectedTab = DashboardTab.Report
                    refreshTabButtons()
                    rebuildTabContent()
                    resetDashboardScrollPosition()
                }

                "achievement" -> {
                    selectedTab = DashboardTab.Achievement
                    refreshTabButtons()
                    rebuildTabContent()
                    resetDashboardScrollPosition()
                }

                "plan" -> {
                    selectedTab = DashboardTab.Plan
                    refreshTabButtons()
                    rebuildTabContent()
                    resetDashboardScrollPosition()
                }
            }
            if (autoAcceptPlanForTest && !isTodayPlanAccepted()) {
                acceptTodayAdvice()
            }
            if (autoStartSessionForTest) {
                requestTrainingStart()
            }
        }
    }

    private fun updateTrainingUi() {
        val inTrainingState = trainingActive || launchCountdownActive
        backButtonView.visibility = if (inTrainingState) View.GONE else View.VISIBLE
        dashboardScrollView.visibility = if (inTrainingState) View.GONE else View.VISIBLE
        statusBarView.visibility = if (trainingActive) View.VISIBLE else View.GONE
        endButtonView.visibility = if (trainingActive) View.VISIBLE else View.GONE
        hintView.visibility = if (inTrainingState) View.VISIBLE else View.GONE
        countdownView.visibility = if (launchCountdownActive) View.VISIBLE else View.GONE
        if (!inTrainingState && resultPopupView.visibility != View.VISIBLE) {
            hintView.visibility = View.GONE
        }
    }

    private fun resetDashboardScrollPosition() {
        dashboardScrollView.post {
            dashboardScrollView.scrollTo(0, 0)
        }
    }

    private fun updateOverlay(snapshot: BlitzModeGameView.OverlaySnapshot) {
        onOverlaySnapshot(snapshot)
    }

    private fun updateTimerDisplay() {
        timerValueView.text = displayRemaining(remainingTrainingMs)
    }

    private fun todayPlan(reports: List<DayReport> = loadReports()): ChallengeDayPlan =
        loadAcceptedPlanForToday() ?: buildCoachAdvice(reports).previewPlan

    private fun BlitzModeGameView.OverlaySnapshot.toCoachOverlaySnapshot(): FatBurnCoachGameView.OverlaySnapshot {
        return FatBurnCoachGameView.OverlaySnapshot(
            totalHits = hits,
            validHits = hits,
            missedBeats = 0,
            accuracy = 1f,
            hps = pps,
            combo = combo,
            bestCombo = bestCombo,
            calories = calories,
            fatBurnGrams = fatBurnGrams,
            roundIndex = 0,
            roundTotal = currentPlan.roundCount,
            phaseLabel = currentPlan.intensity(languageCode),
            beatFitLabel = "",
        )
    }

    private fun acceptTodayAdvice() {
        val todayKey = dayKey(System.currentTimeMillis())
        prefs.edit()
            .putString(KEY_ACCEPTED_PLAN_DAY, todayKey)
            .putString(KEY_ACCEPTED_PLAN_JSON, currentPlan.toJson().toString())
            .apply()
        refreshDashboard()
    }

    private fun regenerateAdvice() {
        prefs.edit()
            .remove(KEY_ACCEPTED_PLAN_DAY)
            .remove(KEY_ACCEPTED_PLAN_JSON)
            .putLong(KEY_ADVICE_VARIANT_SEED, SystemClock.elapsedRealtime())
            .apply()
        refreshDashboard()
    }

    private fun isTodayPlanAccepted(): Boolean = loadAcceptedPlanForToday() != null

    private fun loadAcceptedPlanForToday(): ChallengeDayPlan? {
        val day = prefs.getString(KEY_ACCEPTED_PLAN_DAY, null)
        val raw = prefs.getString(KEY_ACCEPTED_PLAN_JSON, null).orEmpty()
        if (day != dayKey(System.currentTimeMillis()) || raw.isBlank()) {
            return null
        }
        return runCatching { ChallengeDayPlan.fromJson(JSONObject(raw)) }.getOrNull()
    }

    private fun buildCoachAdvice(reports: List<DayReport>): CoachAdvice {
        val dayIndex = currentDayIndex()
        val bodyMetrics = loadBodyMetrics()
        val weekAggregate =
            aggregateWindow(
                reports.filter { sameWeek(it.timestampMs, System.currentTimeMillis()) },
                bodyMetrics.filter { sameWeek(it.timestampMs, System.currentTimeMillis()) },
            )
        val monthAggregate =
            aggregateWindow(
                reports.filter { sameMonth(it.timestampMs, System.currentTimeMillis()) },
                bodyMetrics.filter { sameMonth(it.timestampMs, System.currentTimeMillis()) },
            )
        val lastReport = reports.maxByOrNull { it.timestampMs }
        val streak = currentStreakDays(reports)
        val seed = prefs.getLong(KEY_ADVICE_VARIANT_SEED, 0L).toInt()
        val basePlan = generatePlanForDay(dayIndex)
        val latestMetric = bodyMetrics.maxByOrNull { it.timestampMs }
        val baselineMetric = bodyMetrics.minByOrNull { it.timestampMs }
        val previousMetric =
            bodyMetrics
                .sortedBy { it.timestampMs }
                .dropLast(1)
                .lastOrNull()
        val totalWeightDelta =
            if (latestMetric != null && baselineMetric != null) {
                baselineMetric.weightKg - latestMetric.weightKg
            } else {
                monthAggregate.weightDeltaKg
            }
        val totalWaistDelta =
            if (latestMetric != null && baselineMetric != null) {
                (baselineMetric.waistCm - latestMetric.waistCm).coerceAtLeast(0f)
            } else {
                monthAggregate.estimatedWaistDeltaCm
            }
        val recentWeightDelta =
            if (latestMetric != null && previousMetric != null) {
                previousMetric.weightKg - latestMetric.weightKg
            } else {
                0f
            }
        val recentWaistDelta =
            if (latestMetric != null && previousMetric != null) {
                previousMetric.waistCm - latestMetric.waistCm
            } else {
                0f
            }

        val profile =
            when {
                reports.isEmpty() ->
                    "starter"
                lastReport != null && lastReport.totalHits < basePlan.targetValidHits * 0.62f ->
                    "recover"
                weekAggregate.completedDays >= 4 && weekAggregate.avgHits >= basePlan.targetValidHits * 0.90f && weekAggregate.calories >= 320f ->
                    if ((seed and 1) == 0) "build" else "speed"
                monthAggregate.completedDays >= 10 && streak >= 5 ->
                    "endurance"
                else ->
                    if ((seed and 1) == 0) "steady" else "steady_plus"
            }

        val tunedPlan =
            when (profile) {
                "starter" ->
                    basePlan.copy(
                        phaseLabelZh = "起步适应",
                        phaseLabelEn = "Starter Adapt",
                        titleZh = "轻量激活陪练",
                        titleEn = "Light Activation",
                        focusZh = "先稳住呼吸和出拳节奏，再把训练量慢慢拉起来。",
                        focusEn = "Stabilize breathing and rhythm before lifting the volume.",
                        intensityZh = "轻负荷",
                        intensityEn = "Light Load",
                        roundCount = 4,
                        workSec = 30,
                        restSec = 24,
                        bpm = 106,
                        estimatedCalories = 62f,
                        targetValidHits = 112,
                    )

                "recover" ->
                    basePlan.copy(
                        phaseLabelZh = "恢复修正",
                        phaseLabelEn = "Recovery Reset",
                        titleZh = "节奏重建陪练",
                        titleEn = "Rhythm Reset",
                        focusZh = "昨天总击中偏低，今天先把出拳数量拉回来，再逐步提强度。",
                        focusEn = "Rebuild total hits first, then lift intensity.",
                        intensityZh = "中低负荷",
                        intensityEn = "Moderate-Light",
                        roundCount = 4,
                        workSec = 32,
                        restSec = 24,
                        bpm = 108,
                        estimatedCalories = 68f,
                        targetValidHits = 120,
                    )

                "build" ->
                    basePlan.copy(
                        phaseLabelZh = "强化推进",
                        phaseLabelEn = "Build Forward",
                        titleZh = "代谢推进陪练",
                        titleEn = "Metabolic Push",
                        focusZh = "近一周状态稳定，今天提高总量与持续输出时间。",
                        focusEn = "Recent training is stable, so today raises volume and sustained work.",
                        intensityZh = "中高负荷",
                        intensityEn = "Medium-High",
                        roundCount = 6,
                        workSec = 40,
                        restSec = 18,
                        bpm = 124,
                        estimatedCalories = 104f,
                        targetValidHits = 188,
                    )

                "speed" ->
                    basePlan.copy(
                        phaseLabelZh = "速度刺激",
                        phaseLabelEn = "Speed Stimulus",
                        titleZh = "频率拉升陪练",
                        titleEn = "Cadence Lift",
                        focusZh = "本次重点冲高频率，用更快的稳定连打拉高消耗。",
                        focusEn = "Raise pace with fast but stable combinations.",
                        intensityZh = "高频负荷",
                        intensityEn = "High Cadence",
                        roundCount = 5,
                        workSec = 36,
                        restSec = 18,
                        bpm = 132,
                        estimatedCalories = 98f,
                        targetValidHits = 182,
                    )

                "endurance" ->
                    basePlan.copy(
                        phaseLabelZh = "耐力巩固",
                        phaseLabelEn = "Endurance Solidify",
                        titleZh = "长段燃脂陪练",
                        titleEn = "Long-Set Burn",
                        focusZh = "连续打卡表现不错，今天用更长回合巩固耐力储备。",
                        focusEn = "Your streak is strong, so today lengthens rounds to deepen endurance.",
                        intensityZh = "高强度",
                        intensityEn = "High Intensity",
                        roundCount = 6,
                        workSec = 44,
                        restSec = 16,
                        bpm = 128,
                        estimatedCalories = 116f,
                        targetValidHits = 214,
                    )

                "steady_plus" ->
                    basePlan.copy(
                        phaseLabelZh = "稳态推进",
                        phaseLabelEn = "Steady Progress",
                        titleZh = "平衡燃脂陪练",
                        titleEn = "Balanced Burn",
                        focusZh = "保持稳定输出的同时，把总训练量往上推半档。",
                        focusEn = "Keep your output steady while nudging the total load upward.",
                        intensityZh = "中等负荷",
                        intensityEn = "Moderate",
                        roundCount = 5,
                        workSec = 38,
                        restSec = 20,
                        bpm = 118,
                        estimatedCalories = 86f,
                        targetValidHits = 156,
                    )

                else ->
                    basePlan.copy(
                        phaseLabelZh = "稳态减脂",
                        phaseLabelEn = "Steady Fat Burn",
                        titleZh = "稳态燃脂陪练",
                        titleEn = "Steady Burn",
                        focusZh = "以持续、稳定、可坚持为主，兼顾总击中与消耗。",
                        focusEn = "Stay sustainable, stable, and consistent while balancing total hits and burn.",
                        intensityZh = "中等负荷",
                        intensityEn = "Moderate",
                        roundCount = 5,
                        workSec = 35,
                        restSec = 20,
                        bpm = 114,
                        estimatedCalories = 80f,
                        targetValidHits = 146,
                    )
            }

        val summaryZh =
            when {
                latestMetric == null ->
                    "今天这节课我们先把训练做扎实，练完顺手记一下体重和腰围，我就能把陪练计划调得更贴身。"
                totalWeightDelta >= 1f || totalWaistDelta >= 1f ->
                    "你最近已经把身体状态往前推了一步，今天我们继续稳住输出，把燃脂势头接住。"
                recentWeightDelta >= 0.2f || recentWaistDelta >= 0.3f ->
                    "最近这一两次身体数据在往好的方向走，今天继续把训练做稳，不用急着猛冲。"
                profile == "recover" ->
                    "今天别急着冲量，我们先把总击中数量重新拉稳，状态回来后再提强度。"
                profile == "build" ->
                    "这一周训练完成得不错，今天可以把总量往上推一点，但出拳节奏还是要稳。"
                profile == "speed" ->
                    "今天我们会更强调频率和连打，把心率带起来，让消耗更扎实。"
                profile == "endurance" ->
                    "你已经有连续训练的底子了，今天适合挑战更长一点的输出段。"
                else ->
                    "今天先把呼吸和输出做顺，稳稳练完，比盲目冲强度更值。"
            }
        val summaryEn =
            when {
                latestMetric == null ->
                    "Let's make today's session solid first, then log your weight and waist so the next plan can fit you better."
                totalWeightDelta >= 1f || totalWaistDelta >= 1f ->
                    "Your body trend is already moving forward, so today we keep the output steady and extend that momentum."
                recentWeightDelta >= 0.2f || recentWaistDelta >= 0.3f ->
                    "Recent body metrics are moving in the right direction, so today's goal is to stay steady rather than rush."
                profile == "recover" ->
                    "No need to rush today. We rebuild total output first, then bring the intensity back."
                profile == "build" ->
                    "Your recent week looks solid, so today can nudge the total load upward while keeping the output stable."
                profile == "speed" ->
                    "Today's session leans into pace and cadence to bring your heart rate up and lock in burn."
                profile == "endurance" ->
                    "Your consistency is strong enough for longer work blocks today."
                else ->
                    "Today's job is to smooth out breathing and output before chasing more intensity."
            }
        val reasonZh =
            buildString {
                append(
                    "训练依据：今日 ${if (lastReport == null) "暂无训练记录" else "最近一次总击中 ${lastReport.totalHits} 次"}，本周完成 ${weekAggregate.completedDays} 次，本月累计燃脂 ${formatDecimal(monthAggregate.fatBurnGrams, 1)} g。"
                )
                if (latestMetric != null && baselineMetric != null) {
                    append(" 身体数据：当前体重 ${formatDecimal(latestMetric.weightKg, 1)} kg，较首次 ${signedDelta(totalWeightDelta)} kg；当前腰围 ${formatDecimal(latestMetric.waistCm, 1)} cm，较首次改善 ${formatDecimal(totalWaistDelta, 1)} cm。")
                } else {
                    append(" 你还没有录入身体数据，建议训练后补记体重和腰围，让后续计划更科学。")
                }
            }
        val reasonEn =
            buildString {
                append(
                    "Training basis: ${if (lastReport == null) "no previous session yet" else "last total hits ${lastReport.totalHits}"}; ${weekAggregate.completedDays} sessions this week and ${formatDecimal(monthAggregate.fatBurnGrams, 1)} g fat burned this month."
                )
                if (latestMetric != null && baselineMetric != null) {
                    append(" Current weight ${formatDecimal(latestMetric.weightKg, 1)} kg, delta ${signedDelta(totalWeightDelta)} kg; waist ${formatDecimal(latestMetric.waistCm, 1)} cm, change ${formatDecimal(totalWaistDelta, 1)} cm.")
                } else {
                    append(" No body metrics recorded yet, so logging weight and waist after training will improve future plans.")
                }
            }
        val coachCueZh =
            when {
                latestMetric == null ->
                    "教练提醒：先把这节课稳定完成，结束后记得录入今天的体重和腰围。"
                totalWeightDelta >= 1f || totalWaistDelta >= 1f ->
                    "教练提醒：你已经有进步了，今天不要乱冲，稳稳把总击中做上去。"
                profile == "recover" ->
                    "教练提醒：前两轮先找回呼吸和输出，总击中起来以后再提频率。"
                profile == "speed" ->
                    "教练提醒：今天重在快而不乱，宁可少一点，也要把每次出拳做实。"
                else ->
                    "教练提醒：${tunedPlan.focusZh}"
            }
        val coachCueEn =
            when {
                latestMetric == null ->
                    "Coach note: finish today's class steadily, then record your weight and waist."
                totalWeightDelta >= 1f || totalWaistDelta >= 1f ->
                    "Coach note: progress is already visible, so keep your output calm and controlled."
                profile == "recover" ->
                    "Coach note: spend the first rounds restoring breathing and total output before lifting pace."
                profile == "speed" ->
                    "Coach note: stay fast but clean. Every solid punch matters more than raw speed."
                else ->
                    "Coach note: ${tunedPlan.focusEn}"
            }

        return CoachAdvice(
            variantKey = profile,
            summaryZh = summaryZh,
            summaryEn = summaryEn,
            reasonZh = reasonZh,
            reasonEn = reasonEn,
            coachCueZh = coachCueZh,
            coachCueEn = coachCueEn,
            previewPlan = tunedPlan,
        )
    }

    private fun currentDayIndex(): Int {
        val startDayKey = prefs.getString(KEY_PROGRAM_START_DAY, null)
        val todayKey = dayKey(System.currentTimeMillis())
        if (startDayKey == null) {
            prefs.edit().putString(KEY_PROGRAM_START_DAY, todayKey).apply()
            return 1
        }
        val startTime = parseDayKey(startDayKey)
        val diffDays = ((dayStart(System.currentTimeMillis()) - startTime) / DAY_MS).toInt().coerceAtLeast(0)
        return (diffDays + 1).coerceAtMost(30)
    }

    private fun generatePlanForDay(dayIndex: Int): ChallengeDayPlan {
        val safeDay = dayIndex.coerceIn(1, 30)
        val phase =
            when {
                safeDay <= 10 -> 0
                safeDay <= 20 -> 1
                else -> 2
            }
        val dayInPhase = ((safeDay - 1) % 10) + 1
        val recoveryDay = dayInPhase == 4 || dayInPhase == 8
        val assessmentDay = dayInPhase == 10

        val phaseLabelZh = listOf("适应期", "强化期", "巩固期")[phase]
        val phaseLabelEn = listOf("Adaptation", "Build", "Consolidation")[phase]
        val phaseThemesZh =
            listOf(
                listOf("节奏适应日", "基础连打日", "短时冲刺日", "恢复节奏日", "耐力推进日", "稳控输出日", "连续输出日", "恢复技术日", "爆发节奏日", "阶段评估日"),
                listOf("强化耐力日", "双段冲刺日", "高频输出日", "恢复节奏日", "负荷提升日", "频率压缩日", "持续爆发日", "主动恢复日", "速度巩固日", "阶段评估日"),
                listOf("巩固耐力日", "速度爆发日", "节奏稳定日", "恢复节奏日", "高负荷冲刺日", "终段提速日", "连续输出日", "恢复整合日", "峰值挑战日", "终段评估日"),
            )
        val themeZh = phaseThemesZh[phase][dayInPhase - 1]
        val themeEn =
            when (themeZh) {
                "节奏适应日" -> "Rhythm Adapt"
                "基础连打日" -> "Foundational Combo"
                "短时冲刺日" -> "Short Burst"
                "恢复节奏日" -> "Recovery Rhythm"
                "耐力推进日" -> "Endurance Build"
                "稳控输出日" -> "Controlled Output"
                "连续输出日" -> "Sustained Output"
                "恢复技术日" -> "Recovery Technique"
                "爆发节奏日" -> "Explosive Rhythm"
                "阶段评估日" -> "Stage Review"
                "强化耐力日" -> "Power Endurance"
                "双段冲刺日" -> "Dual Burst"
                "高频输出日" -> "High Frequency"
                "负荷提升日" -> "Load Boost"
                "频率压缩日" -> "Compressed Tempo"
                "持续爆发日" -> "Sustained Burst"
                "主动恢复日" -> "Active Recovery"
                "速度巩固日" -> "Speed Consolidation"
                "终段评估日" -> "Final Review"
                "巩固耐力日" -> "Consolidated Endurance"
                "速度爆发日" -> "Speed Burst"
                "节奏稳定日" -> "Steady Rhythm"
                "高负荷冲刺日" -> "Heavy Sprint"
                "终段提速日" -> "Late Acceleration"
                "恢复整合日" -> "Recovery Integration"
                "峰值挑战日" -> "Peak Challenge"
                else -> "Fat Burn Session"
            }

        val baseWarmup = if (phase == 0) 45 else 55
        val baseRounds = when (phase) { 0 -> 4; 1 -> 5; else -> 6 }
        val baseWork = when (phase) { 0 -> 26; 1 -> 34; else -> 42 }
        val baseRest = when (phase) { 0 -> 22; 1 -> 18; else -> 14 }
        val baseCooldown = if (phase == 2) 50 else 45
        val baseBpm = when (phase) { 0 -> 92; 1 -> 108; else -> 120 }

        val rounds =
            when {
                recoveryDay -> (baseRounds - 1).coerceAtLeast(3)
                assessmentDay -> baseRounds + 1
                else -> baseRounds + (dayInPhase / 4)
            }
        val workSec =
            when {
                recoveryDay -> (baseWork - 4).coerceAtLeast(22)
                assessmentDay -> baseWork + 6
                else -> baseWork + (dayInPhase % 3) * 3
            }
        val restSec =
            when {
                recoveryDay -> baseRest + 6
                assessmentDay -> (baseRest - 2).coerceAtLeast(12)
                else -> (baseRest - dayInPhase / 5).coerceAtLeast(12)
            }
        val bpm =
            when {
                recoveryDay -> baseBpm - 10
                assessmentDay -> baseBpm + 8
                else -> baseBpm + (dayInPhase - 1) * 2
            }
        val loadScore = min(10, max(3, ((rounds * workSec) / 30) + phase * 2 - if (recoveryDay) 1 else 0))
        val intensityZh =
            when {
                loadScore <= 4 -> "低负荷"
                loadScore <= 7 -> "中高负荷"
                else -> "高强度"
            }
        val intensityEn =
            when {
                loadScore <= 4 -> "Low Load"
                loadScore <= 7 -> "Medium-High"
                else -> "High Intensity"
            }
        val totalDurationMin = (baseWarmup + baseCooldown + rounds * workSec + (rounds - 1) * restSec) / 60f
        val estimatedCalories = totalDurationMin * (5.3f + loadScore * 0.38f)
        val expectedHits = rounds * ((workSec * bpm / 60f) * 0.84f)
        val targetValidHits = expectedHits.roundToInt().coerceAtLeast(48)

        val focusZh =
            when {
                recoveryDay -> "降低负荷，稳住节奏与呼吸恢复"
                assessmentDay -> "用完整训练段测出当前耐力上限"
                phase == 0 -> "建立稳定输出与基础拳击耐受度"
                phase == 1 -> "提高持续输出、冲刺频率与切换能力"
                else -> "巩固耐力储备，拉高末段输出稳定性"
            }
        val focusEn =
            when {
                recoveryDay -> "Reduce load and recover rhythm and breathing"
                assessmentDay -> "Measure endurance ceiling with a full training block"
                phase == 0 -> "Build steady output and basic tolerance"
                phase == 1 -> "Raise sustained output and burst frequency"
                else -> "Solidify endurance reserves and late-stage stability"
            }

        return ChallengeDayPlan(
            dayIndex = safeDay,
            phaseLabelZh = phaseLabelZh,
            phaseLabelEn = phaseLabelEn,
            titleZh = themeZh,
            titleEn = themeEn,
            focusZh = focusZh,
            focusEn = focusEn,
            intensityZh = intensityZh,
            intensityEn = intensityEn,
            warmupSec = baseWarmup,
            roundCount = rounds,
            workSec = workSec,
            restSec = restSec,
            cooldownSec = baseCooldown,
            bpm = bpm,
            loadScore = loadScore,
            estimatedCalories = estimatedCalories,
            targetValidHits = targetValidHits,
        )
    }

    private fun ChallengeDayPlan.toSessionConfig(): FatBurnCoachGameView.SessionConfig {
        val phases = ArrayList<FatBurnCoachGameView.PhaseConfig>()
        phases +=
            FatBurnCoachGameView.PhaseConfig(
                phaseKey = "warmup",
                zhLabel = "热身",
                enLabel = "Warm-up",
                durationMs = warmupSec * 1_000L,
                bpm = max(72, bpm - 18),
                hittable = false,
                roundIndex = 0,
                roundTotal = roundCount,
                cueStrength = 0.72f,
            )
        repeat(roundCount) { round ->
            phases +=
                FatBurnCoachGameView.PhaseConfig(
                    phaseKey = "work_${round + 1}",
                    zhLabel = "第${round + 1} 轮",
                    enLabel = "Round ${round + 1}",
                    durationMs = workSec * 1_000L,
                    bpm = bpm + if (round == roundCount - 1) 4 else round,
                    hittable = true,
                    roundIndex = round + 1,
                    roundTotal = roundCount,
                    cueStrength = 1.04f + round * 0.05f,
                )
            if (round < roundCount - 1) {
                phases +=
                    FatBurnCoachGameView.PhaseConfig(
                        phaseKey = "rest_${round + 1}",
                        zhLabel = "恢复",
                        enLabel = "Recover",
                        durationMs = restSec * 1_000L,
                        bpm = max(68, bpm - 30),
                        hittable = false,
                        roundIndex = round + 1,
                        roundTotal = roundCount,
                        cueStrength = 0.56f,
                    )
            }
        }
        phases +=
            FatBurnCoachGameView.PhaseConfig(
                phaseKey = "cooldown",
                zhLabel = "放松",
                enLabel = "Cool-down",
                durationMs = cooldownSec * 1_000L,
                bpm = max(66, bpm - 24),
                hittable = false,
                roundIndex = roundCount,
                roundTotal = roundCount,
                cueStrength = 0.48f,
            )

        return FatBurnCoachGameView.SessionConfig(
            dayIndex = dayIndex,
            zhTitle = "第${dayIndex}天 · $titleZh",
            enTitle = "Day $dayIndex · $titleEn",
            targetValidHits = targetValidHits,
            estimatedCalories = estimatedCalories,
            phases = phases,
        )
    }

    private fun persistDailyReport(
        snapshot: FatBurnCoachGameView.OverlaySnapshot,
        durationSec: Int,
    ) {
        val reports = loadReports().toMutableList()
        val currentTotalCalories = reports.sumOf { it.calories.toDouble() }.toFloat() + snapshot.calories
        val currentTotalFat = reports.sumOf { it.fatBurnGrams.toDouble() }.toFloat() + snapshot.fatBurnGrams
        val metrics = loadBodyMetrics()
        val latestMetric = metrics.maxByOrNull { it.timestampMs }
        val baselineMetric = metrics.minByOrNull { it.timestampMs }
        val currentWeightKg = latestMetric?.weightKg ?: (DEFAULT_BASE_WEIGHT_KG - currentTotalCalories / 7_700f)
        val waistDeltaCm =
            if (latestMetric != null && baselineMetric != null) {
                (baselineMetric.waistCm - latestMetric.waistCm).coerceAtLeast(0f)
            } else {
                currentTotalFat / 600f
            }
        val report =
            DayReport(
                dayIndex = currentPlan.dayIndex,
                timestampMs = System.currentTimeMillis(),
                totalHits = snapshot.totalHits,
                validHits = snapshot.validHits,
                missedBeats = snapshot.missedBeats,
                accuracy = snapshot.accuracy,
                hps = snapshot.hps,
                calories = snapshot.calories,
                fatBurnGrams = snapshot.fatBurnGrams,
                durationSec = durationSec.coerceAtLeast(1),
                grade = evaluateGrade(snapshot, currentPlan),
                estimatedWeightKg = currentWeightKg,
                estimatedWaistDeltaCm = waistDeltaCm,
            )
        reports.removeAll { it.dayIndex == report.dayIndex }
        reports += report
        reports.sortBy { it.dayIndex }
        saveReports(reports)
    }

    private fun maybeUploadCoachSessionToCloud(
        snapshot: FatBurnCoachGameView.OverlaySnapshot,
        durationSeconds: Int,
    ) {
        if (snapshot.totalHits <= 0 || durationSeconds <= 0) {
            return
        }
        CloudTrainingUploader.uploadIfAvailable(
            context = this,
            scope = lifecycleScope,
            report =
                CloudTrainingUploader.buildReport(
                    totalHits = snapshot.totalHits,
                    durationSeconds = durationSeconds,
                    averageFrequency = snapshot.hps,
                    bestBurstCount = snapshot.bestCombo,
                    preferredModeSeconds = if (durationSeconds >= 60) 60 else 30,
                ),
        )
    }

    private fun currentSessionDurationSeconds(): Int =
        (((currentPlan.totalDurationSec * 1_000L - remainingTrainingMs).coerceAtLeast(0L)) / 1000L).toInt().coerceAtLeast(1)

    private fun loadReports(): List<DayReport> {
        val raw = prefs.getString(KEY_DAILY_REPORTS_JSON, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(DayReport.fromJson(array.getJSONObject(index)))
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun saveReports(reports: List<DayReport>) {
        val array = JSONArray()
        reports.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_DAILY_REPORTS_JSON, array.toString()).apply()
    }

    private fun loadReportForDay(dayIndex: Int): DayReport? = loadReports().firstOrNull { it.dayIndex == dayIndex }

    private fun loadBodyMetrics(): List<BodyMetricsEntry> {
        val raw = prefs.getString(KEY_BODY_METRICS_JSON, null).orEmpty()
        if (raw.isBlank()) {
            return emptyList()
        }
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(BodyMetricsEntry.fromJson(array.getJSONObject(index)))
                }
            }
        }.getOrElse { emptyList() }.sortedBy { it.timestampMs }
    }

    private fun saveBodyMetrics(entries: List<BodyMetricsEntry>) {
        val array = JSONArray()
        entries.sortedBy { it.timestampMs }.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_BODY_METRICS_JSON, array.toString()).apply()
    }

    private fun saveTodayBodyMetrics(
        weightKg: Float,
        waistCm: Float,
    ) {
        val todayStart = dayStart(System.currentTimeMillis())
        val entries =
            loadBodyMetrics()
                .filterNot { dayStart(it.timestampMs) == todayStart }
                .toMutableList()
        entries += BodyMetricsEntry(timestampMs = System.currentTimeMillis(), weightKg = weightKg, waistCm = waistCm)
        saveBodyMetrics(entries)
    }

    private fun aggregateWindow(
        reports: List<DayReport>,
        metrics: List<BodyMetricsEntry> = loadBodyMetrics(),
    ): AggregateWindow {
        if (reports.isEmpty()) {
            val latestMetric = metrics.maxByOrNull { it.timestampMs }
            val baselineMetric = metrics.minByOrNull { it.timestampMs }
            val weightValue = latestMetric?.weightKg ?: DEFAULT_BASE_WEIGHT_KG
            val waistValue = latestMetric?.waistCm ?: DEFAULT_BASE_WAIST_CM
            val weightDelta = if (latestMetric != null && baselineMetric != null) baselineMetric.weightKg - latestMetric.weightKg else 0f
            val waistDelta = if (latestMetric != null && baselineMetric != null) (baselineMetric.waistCm - latestMetric.waistCm).coerceAtLeast(0f) else 0f
            return AggregateWindow(
                completedDays = 0,
                calories = 0f,
                fatBurnGrams = 0f,
                avgHits = 0f,
                avgCaloriesPerSession = 0f,
                estimatedWeightKg = weightValue,
                estimatedWaistDeltaCm = waistDelta,
                currentWaistCm = waistValue,
                weightDeltaKg = weightDelta,
            )
        }
        val calories = reports.sumOf { it.calories.toDouble() }.toFloat()
        val fatBurn = reports.sumOf { it.fatBurnGrams.toDouble() }.toFloat()
        val avgHits = reports.map { it.totalHits.toFloat() }.average().toFloat()
        val avgCaloriesPerSession = reports.map { it.calories }.average().toFloat()
        val latestMetric = metrics.maxByOrNull { it.timestampMs }
        val baselineMetric = metrics.minByOrNull { it.timestampMs }
        val currentWeightKg = latestMetric?.weightKg ?: (DEFAULT_BASE_WEIGHT_KG - calories / 7_700f)
        val currentWaistCm = latestMetric?.waistCm ?: (DEFAULT_BASE_WAIST_CM - fatBurn / 600f).coerceAtLeast(40f)
        val weightDeltaKg =
            if (latestMetric != null && baselineMetric != null) {
                baselineMetric.weightKg - latestMetric.weightKg
            } else {
                DEFAULT_BASE_WEIGHT_KG - currentWeightKg
            }
        val waistDeltaCm =
            if (latestMetric != null && baselineMetric != null) {
                (baselineMetric.waistCm - latestMetric.waistCm).coerceAtLeast(0f)
            } else {
                fatBurn / 600f
            }
        return AggregateWindow(
            completedDays = reports.size,
            calories = calories,
            fatBurnGrams = fatBurn,
            avgHits = avgHits,
            avgCaloriesPerSession = avgCaloriesPerSession,
            estimatedWeightKg = currentWeightKg,
            estimatedWaistDeltaCm = waistDeltaCm,
            currentWaistCm = currentWaistCm,
            weightDeltaKg = weightDeltaKg,
        )
    }

    private fun ChallengeDayPlan.toBlitzDifficultyPreset(): BlitzModeGameView.DifficultyPreset =
        when {
            loadScore >= 8 || workSec >= 40 -> BlitzModeGameView.DifficultyPreset.Insane
            loadScore >= 5 || roundCount >= 6 -> BlitzModeGameView.DifficultyPreset.Advanced
            else -> BlitzModeGameView.DifficultyPreset.Beginner
        }

    private fun buildAchievements(reports: List<DayReport>): List<AchievementMilestone> {
        val streak = currentStreakDays(reports)
        val totalCalories = reports.sumOf { it.calories.toDouble() }.toFloat()
        val totalHits = reports.sumOf { it.totalHits }
        val monthAggregate = aggregateWindow(reports)

        return listOf(
            AchievementMilestone("坚持 3 天", "3-Day Streak", "连续 3 天完成课程，建立运动节律。", "Complete 3 days in a row to build rhythm.", streak >= 3, streak / 3f, "$streak / 3"),
            AchievementMilestone("坚持 7 天", "7-Day Streak", "连续 7 天打卡，建立第一层习惯闭环。", "Train for 7 straight days to lock in habit momentum.", streak >= 7, streak / 7f, "$streak / 7"),
            AchievementMilestone("坚持 14 天", "14-Day Streak", "连续 14 天课程，耐力与执行力同步提升。", "Complete 14 days in a row for deeper endurance gains.", streak >= 14, streak / 14f, "$streak / 14"),
            AchievementMilestone("累计燃烧 500 kcal", "Burn 500 kcal", "累计完成 500 kcal 的陪练课程消耗。", "Accumulate 500 kcal burned in coached sessions.", totalCalories >= 500f, totalCalories / 500f, "${formatDecimal(totalCalories, 0)} / 500"),
            AchievementMilestone("累计燃烧 1000 kcal", "Burn 1000 kcal", "累计完成 1000 kcal 的陪练课程消耗。", "Accumulate 1000 kcal burned in coached sessions.", totalCalories >= 1_000f, totalCalories / 1_000f, "${formatDecimal(totalCalories, 0)} / 1000"),
            AchievementMilestone("体重 -1kg", "Weight -1 kg", "从记录的身体数据看，体重较起点下降 1 kg。", "Recorded body metrics show a 1 kg drop from your start point.", monthAggregate.weightDeltaKg >= 1f, monthAggregate.weightDeltaKg / 1f, "${formatDecimal(monthAggregate.weightDeltaKg, 1)} / 1.0"),
            AchievementMilestone("腰围 -1cm", "Waist -1 cm", "从身体记录与训练趋势看，腰围较起点改善 1 cm。", "Body metrics and training trend show a 1 cm waist improvement from your start point.", monthAggregate.estimatedWaistDeltaCm >= 1f, monthAggregate.estimatedWaistDeltaCm / 1f, "${formatDecimal(monthAggregate.estimatedWaistDeltaCm, 1)} / 1.0"),
            AchievementMilestone("累计击中 1000 次", "1000 Total Hits", "累计完成 1000 次击打输出。", "Accumulate 1000 total hits across coached sessions.", totalHits >= 1000, totalHits / 1000f, "$totalHits / 1000"),
        )
    }

    private fun currentStreakDays(reports: List<DayReport>): Int {
        if (reports.isEmpty()) {
            return 0
        }
        val dayKeys = reports.map { dayKey(it.timestampMs) }.toHashSet()
        var cursor = dayStart(System.currentTimeMillis())
        var streak = 0
        while (dayKeys.contains(dayKey(cursor))) {
            streak += 1
            cursor -= DAY_MS
        }
        return streak
    }

    private fun evaluateGrade(
        snapshot: FatBurnCoachGameView.OverlaySnapshot,
        plan: ChallengeDayPlan,
    ): String {
        val hitProgress = snapshot.totalHits / plan.targetValidHits.toFloat().coerceAtLeast(1f)
        return when {
            hitProgress >= 1.02f && snapshot.calories >= plan.estimatedCalories * 0.98f -> "S"
            hitProgress >= 0.88f && snapshot.calories >= plan.estimatedCalories * 0.88f -> "A"
            hitProgress >= 0.68f -> "B"
            else -> "C"
        }
    }

    private fun text(
        zh: String,
        en: String,
        fr: String? = null,
        th: String? = null,
    ): String = fatBurnCoachText(languageCode, zh, en, fr, th)

    private fun displayRemaining(remainingMs: Long): String {
        val safeMs = remainingMs.coerceAtLeast(0L)
        val totalSeconds = safeMs / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes <= 0) {
            "${seconds}${text("秒", "s")}"
        } else {
            "${minutes}${text("分", "m")} ${seconds}${text("秒", "s")}"
        }
    }

    private fun formatDecimal(
        value: Float,
        digits: Int,
    ): String = String.format(Locale.US, "%.${digits}f", value)

    private fun signedDelta(value: Float): String {
        val normalized = if (kotlin.math.abs(value) < 0.05f) 0f else value
        return if (normalized > 0f) "+${formatDecimal(normalized, 1)}" else formatDecimal(normalized, 1)
    }

    private fun formatMetricDate(timestampMs: Long): String =
        SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestampMs))

    private fun dayKey(timestampMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMs))

    private fun parseDayKey(value: String): Long =
        runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value)?.time ?: dayStart(System.currentTimeMillis())
        }.getOrElse { dayStart(System.currentTimeMillis()) }

    private fun dayStart(timestampMs: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun sameDay(
        timestampA: Long,
        timestampB: Long,
    ): Boolean = dayKey(timestampA) == dayKey(timestampB)

    private fun sameWeek(
        timestampA: Long,
        timestampB: Long,
    ): Boolean {
        val calendarA =
            Calendar.getInstance().apply {
                timeInMillis = timestampA
                firstDayOfWeek = Calendar.MONDAY
            }
        val calendarB =
            Calendar.getInstance().apply {
                timeInMillis = timestampB
                firstDayOfWeek = Calendar.MONDAY
            }
        return calendarA.get(Calendar.YEAR) == calendarB.get(Calendar.YEAR) &&
            calendarA.get(Calendar.WEEK_OF_YEAR) == calendarB.get(Calendar.WEEK_OF_YEAR)
    }

    private fun sameMonth(
        timestampA: Long,
        timestampB: Long,
    ): Boolean {
        val calendarA = Calendar.getInstance().apply { timeInMillis = timestampA }
        val calendarB = Calendar.getInstance().apply { timeInMillis = timestampB }
        return calendarA.get(Calendar.YEAR) == calendarB.get(Calendar.YEAR) &&
            calendarA.get(Calendar.MONTH) == calendarB.get(Calendar.MONTH)
    }

    private fun commandButton(
        label: String,
        fillColor: String,
        strokeColor: String,
    ): TextView =
        TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            includeFontPadding = false
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            minLines = 1
            maxLines = 2
            setLineSpacing(0f, 1.06f)
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 11, 15, 1, TypedValue.COMPLEX_UNIT_SP)
            background = roundedFill(fillColor, strokeColor, 20)
            elevation = dp(2).toFloat()
        }

    private fun roundedFill(
        fillColor: String,
        strokeColor: String,
        radiusDp: Int,
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillColor))
            setStroke(dp(1), Color.parseColor(strokeColor))
        }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    private fun horizontalSpace(width: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(width, 1)
        }

    private fun verticalSpace(height: Int): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_SENSITIVITY_LEVEL = "extra_sensitivity_level"
        const val EXTRA_LANGUAGE = "extra_language"
        const val EXTRA_AUTO_ACCEPT_PLAN = "extra_auto_accept_plan"
        const val EXTRA_AUTO_START_SESSION = "extra_auto_start_session"
        const val EXTRA_AUTO_SIMULATE_PUNCHES = "extra_auto_simulate_punches"
        const val EXTRA_AUTO_FINISH_AFTER_MS = "extra_auto_finish_after_ms"
        const val EXTRA_AUTO_SEED_WEIGHT_KG = "extra_auto_seed_weight_kg"
        const val EXTRA_AUTO_SEED_WAIST_CM = "extra_auto_seed_waist_cm"
        const val EXTRA_AUTO_OPEN_TAB = "extra_auto_open_tab"

        private const val PREFS_NAME = "fat_burn_coach"
        private const val KEY_PROGRAM_START_DAY = "program_start_day"
        private const val KEY_DAILY_REPORTS_JSON = "daily_reports_json"
        private const val KEY_BODY_METRICS_JSON = "body_metrics_json"
        private const val KEY_ACCEPTED_PLAN_DAY = "accepted_plan_day"
        private const val KEY_ACCEPTED_PLAN_JSON = "accepted_plan_json"
        private const val KEY_ADVICE_VARIANT_SEED = "advice_variant_seed"
        private const val MAX_DETECTOR_RECOVERY_ATTEMPTS = 8
        private const val DETECTOR_READY_WAIT_TIMEOUT_MS = 2_400L
        private const val DAY_MS = 86_400_000L
        private const val DEFAULT_BASE_WEIGHT_KG = 68f
        private const val DEFAULT_BASE_WAIST_CM = 82f
    }
}













