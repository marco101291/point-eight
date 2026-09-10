package com.pointeight.user.infrastructure;

import com.pointeight.user.domain.AttachmentStyle;
import com.pointeight.user.domain.Gender;
import com.pointeight.user.domain.SeekingType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent representation of the user. It's an infrastructure detail: the {@code User} aggregate
 * doesn't know this class exists.
 */
@Entity
@Table(name = "users")
public class UserJpaEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  // --- Layer 1 ---
  @Column(name = "age", nullable = false)
  private int age;

  @Enumerated(EnumType.STRING)
  @Column(name = "gender", nullable = false, length = 20)
  private Gender gender;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "user_seeking_genders",
      joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "gender", nullable = false, length = 20)
  private Set<Gender> seekingGenders = new LinkedHashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "seeking_type", nullable = false, length = 20)
  private SeekingType seekingType;

  @Column(name = "city", nullable = false)
  private String city;

  @Column(name = "profession", nullable = false)
  private String profession;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "user_hobbies", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "hobby", nullable = false)
  private List<String> hobbies = new ArrayList<>();

  @Column(name = "photo_url", nullable = false, length = 2048)
  private String photoUrl;

  // --- Layer 2: never leaves here toward any response DTO ---
  @Enumerated(EnumType.STRING)
  @Column(name = "attachment_style", nullable = false, length = 20)
  private AttachmentStyle attachmentStyle;

  @Column(name = "attachment_intensity", nullable = false)
  private double attachmentIntensity;

  @Column(name = "gottman_criticism", nullable = false)
  private double criticism;

  @Column(name = "gottman_contempt", nullable = false)
  private double contempt;

  @Column(name = "gottman_defensiveness", nullable = false)
  private double defensiveness;

  @Column(name = "gottman_stonewalling", nullable = false)
  private double stonewalling;

  @Column(name = "infidelity_history", nullable = false)
  private boolean infidelityHistory;

  @Column(name = "relationship_history", nullable = false)
  private int relationshipHistory;

  @Column(name = "active_addiction", nullable = false)
  private boolean activeAddiction;

  @Column(name = "stress_baseline", nullable = false)
  private double stressBaseline;

  @Column(name = "commitment_pace_expectation", nullable = false)
  private double commitmentPaceExpectation;

  @Column(name = "cumulative_confidence_score", nullable = false)
  private double cumulativeConfidenceScore;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserJpaEntity() {
    // Required by JPA.
  }

  UserJpaEntity(UUID id) {
    this.id = id;
  }

  UUID getId() {
    return id;
  }

  int getAge() {
    return age;
  }

  void setAge(int age) {
    this.age = age;
  }

  Gender getGender() {
    return gender;
  }

  void setGender(Gender gender) {
    this.gender = gender;
  }

  Set<Gender> getSeekingGenders() {
    return seekingGenders;
  }

  void setSeekingGenders(Set<Gender> seekingGenders) {
    this.seekingGenders = new LinkedHashSet<>(seekingGenders);
  }

  SeekingType getSeekingType() {
    return seekingType;
  }

  void setSeekingType(SeekingType seekingType) {
    this.seekingType = seekingType;
  }

  String getCity() {
    return city;
  }

  void setCity(String city) {
    this.city = city;
  }

  String getProfession() {
    return profession;
  }

  void setProfession(String profession) {
    this.profession = profession;
  }

  List<String> getHobbies() {
    return hobbies;
  }

  void setHobbies(List<String> hobbies) {
    this.hobbies = new ArrayList<>(hobbies);
  }

  String getPhotoUrl() {
    return photoUrl;
  }

  void setPhotoUrl(String photoUrl) {
    this.photoUrl = photoUrl;
  }

  AttachmentStyle getAttachmentStyle() {
    return attachmentStyle;
  }

  void setAttachmentStyle(AttachmentStyle attachmentStyle) {
    this.attachmentStyle = attachmentStyle;
  }

  double getAttachmentIntensity() {
    return attachmentIntensity;
  }

  void setAttachmentIntensity(double attachmentIntensity) {
    this.attachmentIntensity = attachmentIntensity;
  }

  double getCriticism() {
    return criticism;
  }

  void setCriticism(double criticism) {
    this.criticism = criticism;
  }

  double getContempt() {
    return contempt;
  }

  void setContempt(double contempt) {
    this.contempt = contempt;
  }

  double getDefensiveness() {
    return defensiveness;
  }

  void setDefensiveness(double defensiveness) {
    this.defensiveness = defensiveness;
  }

  double getStonewalling() {
    return stonewalling;
  }

  void setStonewalling(double stonewalling) {
    this.stonewalling = stonewalling;
  }

  boolean isInfidelityHistory() {
    return infidelityHistory;
  }

  void setInfidelityHistory(boolean infidelityHistory) {
    this.infidelityHistory = infidelityHistory;
  }

  int getRelationshipHistory() {
    return relationshipHistory;
  }

  void setRelationshipHistory(int relationshipHistory) {
    this.relationshipHistory = relationshipHistory;
  }

  boolean isActiveAddiction() {
    return activeAddiction;
  }

  void setActiveAddiction(boolean activeAddiction) {
    this.activeAddiction = activeAddiction;
  }

  double getStressBaseline() {
    return stressBaseline;
  }

  void setStressBaseline(double stressBaseline) {
    this.stressBaseline = stressBaseline;
  }

  double getCommitmentPaceExpectation() {
    return commitmentPaceExpectation;
  }

  void setCommitmentPaceExpectation(double commitmentPaceExpectation) {
    this.commitmentPaceExpectation = commitmentPaceExpectation;
  }

  double getCumulativeConfidenceScore() {
    return cumulativeConfidenceScore;
  }

  void setCumulativeConfidenceScore(double cumulativeConfidenceScore) {
    this.cumulativeConfidenceScore = cumulativeConfidenceScore;
  }

  Instant getCreatedAt() {
    return createdAt;
  }

  void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  Instant getUpdatedAt() {
    return updatedAt;
  }

  void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
