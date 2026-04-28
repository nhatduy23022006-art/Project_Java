package com.example.codeanalyzer;

public class Models {
    public static class Submission {
        public long submissionId;
        public String code;
    }

    public static class AnalysisResult {
        public long submissionId;
        public String dsAndAlgos;
        public String usedAI;
        public double confidence;
        public double dsScore;
        public double algoScore;
        public double aiScore;
    }
}
