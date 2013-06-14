package com.atlassian.example.reviewcreator;

/**
 * Class to hold form constants
 */
public final class AdminForm {

    // input names
    public static final String RUN_AS_USER = "runAsUser";
    public static final String EDIT_AS_JSON = "editAsJson";
    public static final String REVIEW_SUBJECT_PREFIX = "reviewSubjectPrefix";
    public static final String REVIEW_DESCRIPTION = "reviewDescription";
    public static final String PATH_PREFIX = "pathPrefix";
    public static final String EXPRESSION = "expression";
    public static final String ENABLED_FOR_PROJECT_KEY = "enabledForProjectKey";
    public static final String USER_REVIEWER = "userReviewer";
    public static final String GROUP_REVIEWER = "userReviewer";
    public static final String NEW_EDIT_NAME = "newEditName";
    public static final String OLD_EDIT_NAME = "oldEditName";
    public static final String EDIT_CONFIG = "editConfig";
    public static final String SELECTED_CONFIG = "selectedConfig";

    // submit button values
    public static final String SUBMIT_SAVE = "save";
    public static final String SUBMIT_SAVE_JSON = "save json";
    public static final String SUBMIT_CANCEL = "cancel";
    public static final String SUBMIT_EDIT = "edit";
    public static final String SUBMIT_EDIT_JSON = "edit as json";
    public static final String SUBMIT_NEW = "new";
    public static final String SUBMIT_DELETE = "delete";

    private AdminForm() {
        throw new UnsupportedOperationException("don't instantiate me");
    }
}
