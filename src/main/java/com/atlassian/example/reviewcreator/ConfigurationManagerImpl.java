package com.atlassian.example.reviewcreator;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import java.util.*;

public class ConfigurationManagerImpl implements ConfigurationManager {

    private final String RUNAS_CONFIG = "org.kuali.autoreview.runAs";

//    private final String PROJECTS_CFG       = "com.example.reviewcreator.projects";
//    private final String COMMITTER_CFG      = "com.example.reviewcreator.crucibleUsers";
//    private final String GROUP_CFG          = "com.example.reviewcreator.crucibleGroups";
//    private final String CREATE_MODE_CFG    = "com.example.reviewcreator.createMode";
//    private final String ITERATIVE_CFG      = "com.example.reviewcreator.iterative";

    // Settings for Kuali Rice AutoReview plugin
    private final String EXPRESSION_CONFIG_MAP = "org.kuali.autoreview.expressionConfigMap";

    transient static final ExpressionReviewConfig prototype;
    static {
        prototype = new ExpressionReviewConfig();
        prototype.setEnabledForPathPrefixes(Arrays.asList("trunk/"));
        prototype.setEnabledForProjectKeys(Arrays.asList("rice"));
        prototype.setFileNameExpressions(Arrays.asList(".*/Test.java"));
        prototype.setUserReviewers(Arrays.asList("gilesp"));
        prototype.setReviewDescription("This review was automatically generated because ...");
        prototype.setReviewSubjectPrefix("Prototype Review");
    }



    public Map<String,String> loadRawExpressionConfigMap() {
        return loadMap(EXPRESSION_CONFIG_MAP);
    }

    public void storeRawExpressionConfigMap(Map<String, String> rawConfigMap) {
        for (Map.Entry<String,String> configEntry : rawConfigMap.entrySet()) {
            // if a configuration is invalid, this will throw an exception
            unmarshalReviewTypeConfig(configEntry.getValue());
        }

        storeMap(EXPRESSION_CONFIG_MAP, rawConfigMap);
    }

    public Map<String,ExpressionReviewConfig> loadExpressionConfigMap() {
        Map<String, ExpressionReviewConfig> expressionConfigMap = new HashMap<String, ExpressionReviewConfig>();

        Map<String,String> storedMap = loadMap(EXPRESSION_CONFIG_MAP);
        if (storedMap != null) for (Map.Entry<String,String> storedEntry : storedMap.entrySet()) {
            expressionConfigMap.put(storedEntry.getKey(), unmarshalReviewTypeConfig(storedEntry.getValue()));
        }

        return expressionConfigMap;
    }

    public void storeExpressionConfigMap(Map<String,ExpressionReviewConfig> expressionConfigMapping) {
        Map<String,String> storableMap = new HashMap<String,String>();

        if (expressionConfigMapping != null) {
            for (Map.Entry<String, ExpressionReviewConfig> configMapEntry : expressionConfigMapping.entrySet()) {
                storableMap.put(configMapEntry.getKey(), marshalReviewTypeConfig(configMapEntry.getValue()));
            }
        }

        storeMap(EXPRESSION_CONFIG_MAP, storableMap);
    }

    private Map<String,String> loadMap(String mapName) {
        Map<String,String> resultsMap = new TreeMap<String, String>();
        final Object value = store.get(mapName);
        if (value != null) {
            if (value instanceof Map) {
                Map<String,String> storedMap = (Map<String, String>)value;
                resultsMap.putAll(storedMap);
            }
        }
        if (!resultsMap.containsKey(CONFIG_PROTOTYPE_KEY)) {
            resultsMap.put(CONFIG_PROTOTYPE_KEY, marshalReviewTypeConfig(prototype));
        }
        return resultsMap;
    }

    private void storeMap(String mapName, Map<String, String> map) {
        store.put(mapName, map);
    }

    private final PluginSettings store;

    public ConfigurationManagerImpl(PluginSettingsFactory settingsFactory) {
        this(settingsFactory.createGlobalSettings());
    }

    ConfigurationManagerImpl(PluginSettings store) {
        this.store = store;
    }

    public String loadRunAsUser() {
        final Object value = store.get(RUNAS_CONFIG);
        return value == null ? null : value.toString();
    }

    public void storeRunAsUser(String username) {
        store.put(RUNAS_CONFIG, username);
    }

//    public CreateMode loadCreateMode() {
//        final Object value = store.get(CREATE_MODE_CFG);
//        try {
//            return value == null ? CreateMode.ALWAYS : CreateMode.valueOf(value.toString());
//        } catch(IllegalArgumentException e) {
//            return CreateMode.ALWAYS;
//        }
//    }
//
//    public void storeCreateMode(CreateMode mode) {
//        store.put(CREATE_MODE_CFG, mode.name());
//    }
//
//    public List<String> loadEnabledProjects() {
//        return loadStringList(PROJECTS_CFG);
//    }
//
//    public void storeEnabledProjects(List<String> projectKeys) {
//        storeStringList(PROJECTS_CFG, projectKeys);
//    }
//
//    public Collection<String> loadCrucibleUserNames() {
//        return loadStringList(COMMITTER_CFG);
//    }
//
//    public void storeCrucibleUserNames(Collection<String> usernames) {
//        storeStringList(COMMITTER_CFG, usernames);
//    }
//
//    public Collection<String> loadCrucibleGroups() {
//        return loadStringList(GROUP_CFG);
//    }
//
//    public void storeCrucibleGroups(Collection<String> groupnames) {
//        storeStringList(GROUP_CFG, groupnames);
//    }
//
//    private void storeStringList(String key, Iterable<String> strings) {
//        store.put(Assertions.notNull("PluginSettings key", key),
//                StringUtils.join(strings.iterator(), ';'));
//    }
//
//    private List<String> loadStringList(String key) {
//        final Object value = store.get(Assertions.notNull("PluginSettings key", key));
//        return value == null ?
//                Collections.<String>emptyList() :
//                Arrays.asList(StringUtils.split(value.toString(), ';'));
//    }
//
//    public boolean loadIterative()
//    {
//        final Object value = store.get(ITERATIVE_CFG);
//        return value == null ? false : Boolean.parseBoolean(value.toString());
//    }
//
//    public void storeIterative(boolean iterative)
//    {
//        store.put(ITERATIVE_CFG, Boolean.toString(iterative));
//    }

    private String marshalReviewTypeConfig(ExpressionReviewConfig rtc) {
        JSON rtc1Json = JSONSerializer.toJSON(rtc);
        String rtcJsonString = rtc1Json.toString(2);
        return rtcJsonString;
    }

    private ExpressionReviewConfig unmarshalReviewTypeConfig(String jsonString) {
        JSONObject rtcJsonObject = JSONObject.fromObject(jsonString);
        ExpressionReviewConfig rtc = (ExpressionReviewConfig)rtcJsonObject.toBean(ExpressionReviewConfig.class);
        return rtc;
    }
}
