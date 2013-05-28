package com.atlassian.example.reviewcreator;

import java.util.Map;

/**
 * Manages plugin settings serialization and persistence.
 * This service is accessed by both the admin servlet and the event listener.
 */
public interface ConfigurationManager {

    // Key for an ExpressionReviewConfig that is strictly used as a prototype and shouldn't be evaluated
    public static final String CONFIG_PROTOTYPE_KEY = "CONFIG_PROTOTYPE";

    String loadRunAsUser();

    void storeRunAsUser(String username);

    public Map<String,String> loadRawExpressionConfigMap();

    /**
     * @return a map where the the expression configurations are the values and the keys are their names.
     * Possibly empty, but Never null.
     */
    Map<String, ExpressionReviewConfig> loadExpressionConfigMap();

    public void storeRawExpressionConfigMap(Map<String,String> rawConfigMap);

    void storeExpressionConfigMap(Map<String, ExpressionReviewConfig> expressionConfigMap);

//    List<String> loadEnabledProjects();
//
//    void storeEnabledProjects(List<String> projectKeys);
//
//    /**
//     * @since   v1.2
//     */
//    Collection<String> loadCrucibleUserNames();
//
//    /**
//     * @since   v1.2
//     */
//    void storeCrucibleUserNames(Collection<String> usernames);
//
//    /**
//     * @since   v1.3
//     */
//    Collection<String> loadCrucibleGroups();
//
//    /**
//     * @since   v1.3
//     */
//    void storeCrucibleGroups(Collection<String> groupnames);
//
//    CreateMode loadCreateMode();
//
//    void storeCreateMode(CreateMode mode);
//
//    /**
//     * @since   v1.4.1
//     */
//    boolean loadIterative();
//
//    /**
//     * @since   v1.4.1
//     */
//    void storeIterative(boolean iterative);
}
