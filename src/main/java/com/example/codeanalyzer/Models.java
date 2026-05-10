package com.example.codeanalyzer;

public class Models {
    public static class Account {
        public long id;
        public String handle;
        public String platform;
        public long lastSubmissionId;
    }

    public static class Submission {
        public long submissionId;
        public String code;
    }

    public static class CodeforcesSubmission {
        public long accountId;
        public String handle;
        public long submissionId;
        public long contestId;
        public String problemIndex;
        public String language;
        public String verdict;
        public long creationTimeSeconds;
        public String sourceCode;
        public long databaseId;
        public boolean isAnalyzed;
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

    public static class AccountEvaluation {
        public String handle;
        public int totalSubmissions;
        public int totalAnalyzed;
        public double avgDsScore;
        public double avgAlgoScore;
        public double avgAiScore;
        public String topDs;
        public String topAlgos;
    }
}
