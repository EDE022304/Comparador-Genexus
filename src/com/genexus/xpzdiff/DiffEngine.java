package com.genexus.xpzdiff;

import java.util.*;

public class DiffEngine {

    public enum ChangeType {
        UNCHANGED, ADDED, DELETED, MODIFIED
    }

    public static class DiffLine {
        public int oldLineNo; // 1-based, 0 if not present
        public int newLineNo; // 1-based, 0 if not present
        public ChangeType type;
        public String oldContent;
        public String newContent;

        public DiffLine(int oldLineNo, int newLineNo, ChangeType type, String oldContent, String newContent) {
            this.oldLineNo = oldLineNo;
            this.newLineNo = newLineNo;
            this.type = type;
            this.oldContent = oldContent;
            this.newContent = newContent;
        }
    }

    public static List<DiffLine> computeDiff(String text1, String text2) {
        String[] lines1 = text1 == null ? new String[0] : text1.split("\r?\n", -1);
        String[] lines2 = text2 == null ? new String[0] : text2.split("\r?\n", -1);

        // Remove trailing empty line if both ended with newline
        if (lines1.length > 0 && lines1[lines1.length - 1].isEmpty() && text1 != null && !text1.isEmpty()) {
            lines1 = Arrays.copyOf(lines1, lines1.length - 1);
        }
        if (lines2.length > 0 && lines2[lines2.length - 1].isEmpty() && text2 != null && !text2.isEmpty()) {
            lines2 = Arrays.copyOf(lines2, lines2.length - 1);
        }

        int m = lines1.length;
        int n = lines2.length;

        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (lines1[i].equals(lines2[j])) {
                    dp[i + 1][j + 1] = dp[i][j] + 1;
                } else {
                    dp[i + 1][j + 1] = Math.max(dp[i + 1][j], dp[i][j + 1]);
                }
            }
        }

        List<DiffLine> result = new ArrayList<>();
        int i = m, j = n;
        List<DiffLine> rawOps = new ArrayList<>();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && lines1[i - 1].equals(lines2[j - 1])) {
                rawOps.add(new DiffLine(i, j, ChangeType.UNCHANGED, lines1[i - 1], lines2[j - 1]));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                rawOps.add(new DiffLine(0, j, ChangeType.ADDED, null, lines2[j - 1]));
                j--;
            } else if (i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j])) {
                rawOps.add(new DiffLine(i, 0, ChangeType.DELETED, lines1[i - 1], null));
                i--;
            }
        }

        Collections.reverse(rawOps);

        // Group consecutive DELETED followed by ADDED into MODIFIED for better visual display
        int idx = 0;
        while (idx < rawOps.size()) {
            DiffLine current = rawOps.get(idx);

            if (current.type == ChangeType.DELETED) {
                // Collect consecutive deleted lines
                List<DiffLine> deletedGroup = new ArrayList<>();
                while (idx < rawOps.size() && rawOps.get(idx).type == ChangeType.DELETED) {
                    deletedGroup.add(rawOps.get(idx));
                    idx++;
                }

                // Collect consecutive added lines right after
                List<DiffLine> addedGroup = new ArrayList<>();
                while (idx < rawOps.size() && rawOps.get(idx).type == ChangeType.ADDED) {
                    addedGroup.add(rawOps.get(idx));
                    idx++;
                }

                if (!addedGroup.isEmpty()) {
                    // Pair them up as MODIFIED where possible
                    int minSize = Math.min(deletedGroup.size(), addedGroup.size());
                    for (int k = 0; k < minSize; k++) {
                        DiffLine del = deletedGroup.get(k);
                        DiffLine add = addedGroup.get(k);
                        result.add(new DiffLine(del.oldLineNo, add.newLineNo, ChangeType.MODIFIED, del.oldContent, add.newContent));
                    }
                    for (int k = minSize; k < deletedGroup.size(); k++) {
                        result.add(deletedGroup.get(k));
                    }
                    for (int k = minSize; k < addedGroup.size(); k++) {
                        result.add(addedGroup.get(k));
                    }
                } else {
                    result.addAll(deletedGroup);
                }
            } else {
                result.add(current);
                idx++;
            }
        }

        return result;
    }
}
