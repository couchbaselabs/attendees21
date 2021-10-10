package com.couchbase.connect21.pollbot;


import java.time.Instant;
import java.util.List;
import java.util.Objects;


public class Poll {

  private String id;
  private boolean unlocked;
  private long unlockTime;
  private boolean scored;
  private int index;
  private int award;
  private String question;
  private List<Answer> answers;

  public Poll() {

  }

  public Poll(String question) {
    this.question = question;
  }

  public boolean isScored() {
    return this.scored;
  }

  public void setScored(boolean isScored) {
    this.scored = isScored;
  }
  public String getId() {
    return this.id;
  }

  public boolean isUnlocked() {
    return unlocked;
  }

  public void setUnlocked(boolean unlocked) {
    this.unlocked = unlocked;
    if (this.unlocked) {
      this.unlockTime = Instant.now().getEpochSecond();
    }
  }

  public long getUnlockTime() {
    return unlockTime;
  }

  public int getIndex() {
    return index;
  }

  public void setIndex(int index) {
    this.index = index;
  }

  public int getAward() {
    return award;
  }

  public void setAward(int award) {
    this.award = award;
  }

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public List<Answer> getAnswers() {
    return answers;
  }

  public void setAnswers(List<Answer> answers) {
    this.answers = answers;
  }

  public boolean isAnswerCorrect(String answer) {
    return getAnswers().stream()
      .filter(Answer::isCorrect)
      .anyMatch(correct -> Objects.equals(answer, correct.getText()));
  }

  public static class Answer {
    private String text;
    private boolean correct;

    public Answer() {
    }

    public Answer(String text, boolean correct) {
      this.text = text;
      this.correct = correct;
    }

    public String getText() {
      return text;
    }

    public void setText(String text) {
      this.text = text;
    }

    public boolean isCorrect() {
      return correct;
    }

    public void setCorrect(boolean correct) {
      this.correct = correct;
    }

  }
}
