package org.example.Service;

public enum QuestionType {
    MCQ("Multiple choice"),
    SHORT_ANSWER("Short answer");

    private final String label;

    QuestionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
