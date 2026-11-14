package com.chinmai.yogasense.data.db

import androidx.room.TypeConverter
import com.chinmai.yogasense.asana.Asana
import com.chinmai.yogasense.coaching.VoiceMode

/**
 * Explicit enum ↔ name converters. Names (not ordinals) are stored so reordering an enum never
 * changes stored meaning. Renaming a constant requires a migration.
 */
class Converters {
    @TypeConverter fun fromAsana(v: Asana): String = v.name
    @TypeConverter fun toAsana(v: String): Asana = Asana.valueOf(v)
    @TypeConverter fun fromVoiceMode(v: VoiceMode): String = v.name
    @TypeConverter fun toVoiceMode(v: String): VoiceMode = VoiceMode.valueOf(v)
    @TypeConverter fun fromProfileStatus(v: ProfileStatus): String = v.name
    @TypeConverter fun toProfileStatus(v: String): ProfileStatus = ProfileStatus.valueOf(v)
    @TypeConverter fun fromExperience(v: ExperienceLevel): String = v.name
    @TypeConverter fun toExperience(v: String): ExperienceLevel = ExperienceLevel.valueOf(v)
    @TypeConverter fun fromCompletion(v: SessionCompletion): String = v.name
    @TypeConverter fun toCompletion(v: String): SessionCompletion = SessionCompletion.valueOf(v)
    @TypeConverter fun fromInstrument(v: Instrument): String = v.name
    @TypeConverter fun toInstrument(v: String): Instrument = Instrument.valueOf(v)
    @TypeConverter fun fromConsentType(v: ConsentType): String = v.name
    @TypeConverter fun toConsentType(v: String): ConsentType = ConsentType.valueOf(v)
    @TypeConverter fun fromConsentDecision(v: ConsentDecision): String = v.name
    @TypeConverter fun toConsentDecision(v: String): ConsentDecision = ConsentDecision.valueOf(v)
    @TypeConverter fun fromHeightUnit(v: com.chinmai.yogasense.profile.HeightUnit): String = v.name
    @TypeConverter fun toHeightUnit(v: String): com.chinmai.yogasense.profile.HeightUnit = com.chinmai.yogasense.profile.HeightUnit.valueOf(v)
    @TypeConverter fun fromWeightUnit(v: com.chinmai.yogasense.profile.WeightUnit): String = v.name
    @TypeConverter fun toWeightUnit(v: String): com.chinmai.yogasense.profile.WeightUnit = com.chinmai.yogasense.profile.WeightUnit.valueOf(v)
    @TypeConverter fun fromFrequency(v: com.chinmai.yogasense.profile.PracticeFrequency?): String? = v?.name
    @TypeConverter fun toFrequency(v: String?): com.chinmai.yogasense.profile.PracticeFrequency? = v?.let { com.chinmai.yogasense.profile.PracticeFrequency.valueOf(it) }
    @TypeConverter fun fromOnboardingStatus(v: com.chinmai.yogasense.profile.OnboardingStatus): String = v.name
    @TypeConverter fun toOnboardingStatus(v: String): com.chinmai.yogasense.profile.OnboardingStatus = com.chinmai.yogasense.profile.OnboardingStatus.valueOf(v)
    @TypeConverter fun fromGoal(v: com.chinmai.yogasense.profile.WellnessGoal): String = v.name
    @TypeConverter fun toGoal(v: String): com.chinmai.yogasense.profile.WellnessGoal = com.chinmai.yogasense.profile.WellnessGoal.valueOf(v)
    @TypeConverter fun fromBodyArea(v: com.chinmai.yogasense.profile.BodyArea): String = v.name
    @TypeConverter fun toBodyArea(v: String): com.chinmai.yogasense.profile.BodyArea = com.chinmai.yogasense.profile.BodyArea.valueOf(v)
    @TypeConverter fun fromIntent(v: com.chinmai.yogasense.profile.BodyAreaIntent?): String? = v?.name
    @TypeConverter fun toIntent(v: String?): com.chinmai.yogasense.profile.BodyAreaIntent? = v?.let { com.chinmai.yogasense.profile.BodyAreaIntent.valueOf(it) }
}
