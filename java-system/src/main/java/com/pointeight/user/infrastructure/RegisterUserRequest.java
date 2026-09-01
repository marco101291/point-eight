package com.pointeight.user.infrastructure;

import com.pointeight.user.domain.AttachmentStyle;
import com.pointeight.user.domain.CommunicationProfile;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.Profile;
import com.pointeight.user.domain.SeekingType;
import com.pointeight.user.domain.SimulationParameters;
import com.pointeight.user.domain.TraitDerivation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

/**
 * User registration. Layer 2 fields are optional and <b>write-only</b>: they're accepted here, but
 * no DTO ever returns them. If they're not provided, the System derives them from the profile.
 */
public record RegisterUserRequest(
    @Min(18) @Max(120) int age,
    @NotNull Gender gender,
    @NotEmpty Set<Gender> seekingGenders,
    SeekingType seekingType,
    @NotBlank String city,
    @NotBlank String profession,
    List<String> hobbies,
    // --- Layer 2, optional ---
    AttachmentStyle attachmentStyle,
    Double attachmentIntensity,
    CommunicationProfile communicationProfile,
    Boolean infidelityHistory,
    Integer relationshipHistory,
    Boolean activeAddiction,
    Double stressBaseline,
    Double commitmentPaceExpectation) {

  public Profile toProfile() {
    return new Profile(age, gender, seekingGenders, seekingType, city, profession, hobbies);
  }

  /**
   * Explicit Layer 2, or {@code null} so the aggregate applies the default derivation. Derivable
   * traits fall back to {@link TraitDerivation} field by field when not provided.
   */
  public SimulationParameters toSimulationParameters() {
    boolean anyProvided =
        attachmentStyle != null
            || attachmentIntensity != null
            || communicationProfile != null
            || infidelityHistory != null
            || relationshipHistory != null
            || activeAddiction != null
            || stressBaseline != null
            || commitmentPaceExpectation != null;
    if (!anyProvided) {
      return null;
    }
    return new SimulationParameters(
        attachmentStyle == null ? AttachmentStyle.SECURE : attachmentStyle,
        attachmentIntensity == null ? 0.5 : attachmentIntensity,
        communicationProfile == null ? CommunicationProfile.neutral() : communicationProfile,
        infidelityHistory != null && infidelityHistory,
        relationshipHistory == null ? 0 : relationshipHistory,
        activeAddiction != null && activeAddiction,
        stressBaseline == null ? TraitDerivation.stressBaseline(profession, age) : stressBaseline,
        commitmentPaceExpectation == null
            ? TraitDerivation.commitmentPaceExpectation(age)
            : commitmentPaceExpectation);
  }
}
