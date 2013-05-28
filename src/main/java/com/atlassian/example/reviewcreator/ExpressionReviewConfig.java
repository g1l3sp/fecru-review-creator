package com.atlassian.example.reviewcreator;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
* Configuration object for an automatic review generated based on filename matching expressions
*/
public class ExpressionReviewConfig {

    private String reviewSubjectPrefix;
    private String reviewDescription;
    private List<String> fileNameExpressions;
    private List<String> enabledForProjectKeys;
    private List<String> enabledForPathPrefixes;
    private List<String> userReviewers;
    private List<String> groupReviewers;

    /**
     * @param fileNameExpressions List of expressions that will be used to match file names in commits
     */
    public void setFileNameExpressions(List<String> fileNameExpressions) {
        this.fileNameExpressions = fileNameExpressions;
    }

    /**
     * @return List of expressions that will be used to match file names in commits
     */
    public List<String> getFileNameExpressions() {
        return fileNameExpressions;
    }

    /**
     * @param enabledForProjectKeys List of project keys that this review type is enabled for
     */
    public void setEnabledForProjectKeys(List<String> enabledForProjectKeys) {
        this.enabledForProjectKeys = enabledForProjectKeys;
    }

    /**
     * @return List of project keys that this review type is enabled for
     */
    public List<String> getEnabledForProjectKeys() {
        return enabledForProjectKeys;
    }

    /**
     * Get the list of paths to branches for which we'll automatically create reviews
     * @return the list of paths
     */
    public List<String> getEnabledForPathPrefixes() {
        return enabledForPathPrefixes;
    }

    public void setEnabledForPathPrefixes(List<String> enabledForPathPrefixes) {
        this.enabledForPathPrefixes = enabledForPathPrefixes;
    }

    /**
     * @param reviewDescription Description that will be placed on the review
     */
    public void setReviewDescription(String reviewDescription) {
        this.reviewDescription = reviewDescription;
    }

    /**
     * @return Description that will be placed on the review
     */
    public String getReviewDescription() {
        return reviewDescription;
    }

    /**
     *
     * @param reviewSubjectPrefix Subject line for review
     */
    public void setReviewSubjectPrefix(String reviewSubjectPrefix) {
        this.reviewSubjectPrefix = reviewSubjectPrefix;
    }

    /**
     * @return Subject line for review
     */
    public String getReviewSubjectPrefix() {
        return reviewSubjectPrefix;
    }

    /**
     * @param groupReviewers crucible groups that will be on generated reviews
     */
    public void setGroupReviewers(List<String> groupReviewers) {
        this.groupReviewers = groupReviewers;
    }

    /**
     * @return crucible groups that will be on generated reviews
     */
    public List<String> getGroupReviewers() {
        return groupReviewers;
    }

    /**
     * @param userReviewers crucible users that will be on generated reviews
     */
    public void setUserReviewers(List<String> userReviewers) {
        this.userReviewers = userReviewers;
    }

    /**
     * @return crucible users that will be on generated reviews
     */
    public List<String> getUserReviewers() {
        return userReviewers;
    }



    /**
     * to be a valid config, a subject MUST be defined,
     * there must be at least one group or member reviewer,
     * AND all the expressions must be valid regexps.
     * @throws ReviewValidationException if the config is not valid
     */
    public void validateConfig() throws ReviewValidationException {
        // Also, all the expressions must be valid regexps

        // validate subject
        if (StringUtils.isEmpty(getReviewSubjectPrefix())) {
            throw new ReviewValidationException("reviewSubjectPrefix can not be empty");
        }

        // validate reviewers
        if (CollectionUtils.isEmpty(getUserReviewers()) && CollectionUtils.isEmpty(getGroupReviewers())) {
            throw new ReviewValidationException("there must be at least one user or group reviewer");
        }

        // validate expressions
        if (!CollectionUtils.isEmpty(getFileNameExpressions())) {
            for (String expression : getFileNameExpressions()) {
                try {
                    Pattern.compile(expression);
                } catch (PatternSyntaxException pse) {
                    throw new ReviewValidationException(
                            "fileNameExpressions contained invalid expression " + expression, pse);
                }
            }
        }

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ExpressionReviewConfig that = (ExpressionReviewConfig) o;

        EqualsBuilder.reflectionEquals(this, that);

        return true;
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    public static class ReviewValidationException extends Exception {
        private final String reason;

        public ReviewValidationException(String reason) {
            super(reason);
            this.reason = reason;
        }

        public ReviewValidationException(String reason, Throwable cause) {
            super(reason, cause);
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }
}
