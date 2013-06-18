package org.kuali.crucible.plugin.reviewgenerator;

import com.atlassian.crucible.spi.services.*;
import com.atlassian.fisheye.plugin.web.helpers.VelocityHelper;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.velocity.app.FieldMethodizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

public class AdminServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(AdminServlet.class);

    private static final String FEEDBACK_MESSAGE_PARM = "feedbackMessage";
    public static final String MAIN_BUTTONS_ANCHOR = "#mainButtons";

    private final ProjectService projectService;
    private final ImpersonationService impersonator;
    private final UserService userService;
    private final VelocityHelper velocity;
    private final ConfigurationManager config;
    // private final WebResourceManager webResourceManager;

    public AdminServlet(
            ConfigurationManager config,
            ProjectService projectService,
            ImpersonationService impersonator,
            UserService userService,
            //WebResourceManager webResourceManager,
            VelocityHelper velocity) {
        
        this.projectService = projectService;
        this.impersonator = impersonator;
        this.userService = userService;
        this.velocity = velocity;
        this.config = config;
        //this.webResourceManager = webResourceManager;
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        final Map<String, Object> params = new HashMap<String, Object>();

        // allow access to constants in AdminForm from our velocity template
        params.put("adminForm", new FieldMethodizer(AdminForm.class.getName()));

//        params.put("webResourceManager", webResourceManager);

        final String runAsUser = config.loadRunAsUser();
        if (!StringUtils.isEmpty(runAsUser)) {
            params.put(AdminForm.RUN_AS_USER, runAsUser);

            impersonator.doAsUser(null, runAsUser, new Operation<Void, RuntimeException>() {
                public Void perform() throws RuntimeException {
                    params.put("projects", loadProjects());
                    return null;
                }
            });

            Map<String,String> rawConfigMap = config.loadRawExpressionConfigMap();
            params.put("rawConfigMap", rawConfigMap);
            Map<String, ExpressionReviewConfig> configMap = config.loadExpressionConfigMap();
            params.put("configMap", configMap);

            String feedbackMessage = request.getParameter(FEEDBACK_MESSAGE_PARM);
            if (!StringUtils.isEmpty(feedbackMessage)) {
                params.put(FEEDBACK_MESSAGE_PARM, feedbackMessage.trim());
            }

            String editAsJsonValue = request.getParameter(AdminForm.EDIT_AS_JSON);
            if (!StringUtils.isEmpty(editAsJsonValue)) {
                params.put(AdminForm.EDIT_AS_JSON, editAsJsonValue);
            }

            String editName = request.getParameter("editName");
            if (!StringUtils.isEmpty(editName)) {
                String editConfig = rawConfigMap.get(editName);
                params.put("editName", editName);
                params.put("editConfig", editConfig);
            }
            if ("true".equals(request.getParameter("editNew"))) {
                params.put("editNew", "true");
            }

            params.put("contextPath", request.getContextPath());
            params.put("stringUtils", new StringUtils());
        }

        response.setContentType("text/html");
        velocity.renderVelocityTemplate("templates/admin.vm", params, response.getWriter());
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {

        String submitType = req.getParameter("submit");
        if (AdminForm.SUBMIT_SAVE.equals(submitType)) {
            handleSave(req, resp);
        } else if (AdminForm.SUBMIT_SAVE_JSON.equals(submitType)) {
            handleSaveJson(req, resp);
        } else if (AdminForm.SUBMIT_EDIT_JSON.equals(submitType)) {
            handleEditJson(req, resp);
        } else if (AdminForm.SUBMIT_EDIT.equals(submitType)) {
            handleEdit(req, resp);
        } else if (AdminForm.SUBMIT_NEW.equals(submitType)) {
            handleNew(req, resp);
        } else if (AdminForm.SUBMIT_DELETE.equals(submitType)) {
            handleDelete(req, resp);
        } else {
            // no need to explicitly handle "cancel"
            resp.sendRedirect("./reviewgenerator");
        }
    }

    protected void postRedirect(final HttpServletResponse resp, String queryParameters) throws IOException {
        if (StringUtils.isEmpty(queryParameters)) {
            queryParameters = "";
        }
        resp.sendRedirect("./reviewgenerator?" + queryParameters);
    }

    protected void handleSave(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        StringBuilder feedbackMessageBuilder = new StringBuilder();

        final String runAsUser = req.getParameter(AdminForm.RUN_AS_USER);
        config.storeRunAsUser(runAsUser);

        String newEditName = req.getParameter(AdminForm.NEW_EDIT_NAME);

        if (newEditName != null) {
            String editConfig = req.getParameter("editConfig");

            // extract the expression review config from the form
            ExpressionReviewConfig expressionReviewConfig = extractExpressionReviewConfig(req);

            try {
                // if the config is invalid, we'll bail and put a message together
                expressionReviewConfig.validateConfig();

                if (!StringUtils.isEmpty(newEditName)) {
                    Map<String, ExpressionReviewConfig> configs = config.loadExpressionConfigMap();

                    String oldEditName = req.getParameter(AdminForm.OLD_EDIT_NAME);
                    if (!StringUtils.isEmpty(oldEditName) && !StringUtils.isEmpty(newEditName)) {
                        if (!oldEditName.trim().equals(newEditName.trim())) {
                            configs.remove(oldEditName.trim());
                            feedbackMessageBuilder.append("Deleted configuration '");
                            feedbackMessageBuilder.append(oldEditName);
                            feedbackMessageBuilder.append("', ");
                        }
                    }
                    configs.put(newEditName.trim(), expressionReviewConfig);

                    try {
                        config.storeExpressionConfigMap(configs);
                        feedbackMessageBuilder.append("Stored configuration '");
                        feedbackMessageBuilder.append(newEditName);
                        feedbackMessageBuilder.append("'");
                    } catch (Exception e) {
                        feedbackMessageBuilder.append("unable to store configuration '");
                        feedbackMessageBuilder.append(newEditName);
                        feedbackMessageBuilder.append("', invalid format?");
                    }

                } else {
                    feedbackMessageBuilder.append("No named configuration to store");
                }

            } catch (ExpressionReviewConfig.ReviewValidationException e) {
                feedbackMessageBuilder.append("unable to store configuration '");
                feedbackMessageBuilder.append(newEditName);
                feedbackMessageBuilder.append("', ");
                feedbackMessageBuilder.append(e.getReason());
            }
        }

        postRedirect(resp, FEEDBACK_MESSAGE_PARM + "=" + feedbackMessageBuilder + MAIN_BUTTONS_ANCHOR);
    }

    /**
     * Pull out form data into an ExpressionReviewConfig instance
     *
     * @param req the HttpServletRequest containing the form data
     * @return the extracted ExpressionReviewConfig
     */
    private ExpressionReviewConfig extractExpressionReviewConfig(HttpServletRequest req) {
        // Extract form data to ExpressionReviewConfig object
        ExpressionReviewConfig expressionReviewConfig = new ExpressionReviewConfig();
        expressionReviewConfig.setReviewSubjectPrefix(req.getParameter(AdminForm.REVIEW_SUBJECT_PREFIX));
        expressionReviewConfig.setReviewDescription(req.getParameter(AdminForm.REVIEW_DESCRIPTION));

        // extract indexed parameters
        expressionReviewConfig.setEnabledForPathPrefixes(extractIndexedParameters(req, 0, 9, AdminForm.PATH_PREFIX));
        expressionReviewConfig.setFileNameExpressions(extractIndexedParameters(req, 0, 9, AdminForm.EXPRESSION));
        expressionReviewConfig.setEnabledForProjectKeys(extractIndexedParameters(req, 0, 9, AdminForm.ENABLED_FOR_PROJECT_KEY));
        expressionReviewConfig.setUserReviewers(extractIndexedParameters(req, 0, 9, AdminForm.USER_REVIEWER));
        expressionReviewConfig.setGroupReviewers(extractIndexedParameters(req, 0, 9, AdminForm.GROUP_REVIEWER));

        return expressionReviewConfig;
    }

    /**
     * Extracts to a list parameter values whose names follow the convention elementKeyPrefixN where N is an integer.
     * The resulting list is the size of the number of non-blank elements extracted from the request, so the indices
     * of list elements may not match the indices of the parameters they were extracted from!
     *
     * @param req the HttpServletRequest
     * @param minIndex the minimum index to check
     * @param maxIndex the maximum index to check
     * @param elementKeyPrefix the parameter set prefix
     * @return a list containing all the values for the indexed parameters.  Not guaranteed to be maxIndex-minIndex in size!
     */
    private List<String> extractIndexedParameters(HttpServletRequest req, int minIndex, int maxIndex, String elementKeyPrefix) {
        List<String> elements = new ArrayList<String>(10);
        for (int i=minIndex; i<=maxIndex; i++) {
            String elementValue = req.getParameter(elementKeyPrefix + i);
            if (!StringUtils.isEmpty(elementValue)) {
                elements.add(elementValue);
            }
        }
        return elements;
    }

    protected void handleSaveJson(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        StringBuilder feedbackMessageBuilder = new StringBuilder();

        final String runAsUser = req.getParameter(AdminForm.RUN_AS_USER);
        config.storeRunAsUser(runAsUser);

        String newEditName = req.getParameter(AdminForm.NEW_EDIT_NAME);
        String editConfig = req.getParameter(AdminForm.EDIT_CONFIG);

        if (!StringUtils.isEmpty(newEditName)) {
            Map<String,String> rawConfig = config.loadRawExpressionConfigMap();
            String oldEditName = req.getParameter(AdminForm.OLD_EDIT_NAME);
            if (!StringUtils.isEmpty(oldEditName) && !StringUtils.isEmpty(newEditName)) {
                if (!oldEditName.trim().equals(newEditName.trim())) {
                    rawConfig.remove(oldEditName.trim());
                    feedbackMessageBuilder.append("Deleted configuration '");
                    feedbackMessageBuilder.append(oldEditName);
                    feedbackMessageBuilder.append("', ");
                }
            }
            rawConfig.put(newEditName.trim(), (editConfig == null) ? null : editConfig.trim());

            try {
                config.storeRawExpressionConfigMap(rawConfig);
                feedbackMessageBuilder.append("Stored configuration '");
                feedbackMessageBuilder.append(newEditName);
                feedbackMessageBuilder.append("'");
            } catch (Exception e) {
                feedbackMessageBuilder.append("unable to store configuration '");
                feedbackMessageBuilder.append(newEditName);
                feedbackMessageBuilder.append("', invalid format?");
            }

        } else {
            feedbackMessageBuilder.append("No named configuration to store");
        }

        postRedirect(resp, FEEDBACK_MESSAGE_PARM + "=" + feedbackMessageBuilder + MAIN_BUTTONS_ANCHOR);
    }

    protected void handleDelete(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        StringBuilder requestParameters = new StringBuilder();

        String selectedConfig = req.getParameter(AdminForm.SELECTED_CONFIG);
        if (!StringUtils.isEmpty(selectedConfig)) {
            Map<String,String> rawConfigMap = config.loadRawExpressionConfigMap();
            rawConfigMap.remove(selectedConfig.trim());
            config.storeRawExpressionConfigMap(rawConfigMap);
            requestParameters.append(FEEDBACK_MESSAGE_PARM+"=Deleted configuration '");
            requestParameters.append(selectedConfig.trim());
            requestParameters.append("'");
        } else {
            requestParameters.append(FEEDBACK_MESSAGE_PARM+"=No configuration selected");
        }
        requestParameters.append(MAIN_BUTTONS_ANCHOR);

        postRedirect(resp, requestParameters.toString());
    }

    protected void handleNew(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {

        postRedirect(resp, "editNew=true" +"&"+ FEEDBACK_MESSAGE_PARM +
                "=Template configuration loaded, give it a name and save it" +
                MAIN_BUTTONS_ANCHOR);
    }

    protected void handleEditJson(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        StringBuilder requestParameters = new StringBuilder();

        String selectedConfig = req.getParameter(AdminForm.SELECTED_CONFIG);
        if (!StringUtils.isEmpty(selectedConfig)) {
            requestParameters.append("editAsJson=true");
            requestParameters.append("&editName=");
            requestParameters.append(selectedConfig.trim());
            requestParameters.append("&"+FEEDBACK_MESSAGE_PARM+"=Editing configuration '");
            requestParameters.append(selectedConfig.trim());
            requestParameters.append("'");
            requestParameters.append(MAIN_BUTTONS_ANCHOR);
        }

        postRedirect(resp, requestParameters.toString());
    }

    protected void handleEdit(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        StringBuilder requestParameters = new StringBuilder();

        String selectedConfig = req.getParameter(AdminForm.SELECTED_CONFIG);
        if (!StringUtils.isEmpty(selectedConfig)) {
            requestParameters.append("editName=");
            requestParameters.append(selectedConfig.trim());
            requestParameters.append("&"+FEEDBACK_MESSAGE_PARM+"=Editing configuration '");
            requestParameters.append(selectedConfig.trim());
            requestParameters.append("'");
            requestParameters.append(MAIN_BUTTONS_ANCHOR);
        }

        postRedirect(resp, requestParameters.toString());
    }

    /**
     * @param crucibleUsernames
     * @return  the (sub)set of usernames that exist in the system. The names
     * present in the specified list that are not present in the returned list
     * represent invalid usernames.
     */
    private Collection<String> getValidatedUsernames(Collection<String> crucibleUsernames) {

        return Collections2.filter(crucibleUsernames, new Predicate<String>() {
            public boolean apply(String username) {
                try {
                    userService.getUser(username);
                    return true;
                } catch (NotFoundException nfe) {
                    // Not very good practice to use exceptions for flow
                    // control, but it's the only way to detect the existence
                    // of a Crucible user.
                    return false;
                } catch (ServerException se) {
                    throw new RuntimeException(String.format(
                            "Error validating Crucible user \"%s\": %s", username, se.getMessage()), se);
                }
            }
        });
    }

    /**
     * Returns a set of all projects.
     * Note: this method must be run as a valid Crucible user.
     *
     * @return
     */
    private Set<Project> loadProjects() {

//        final List<String> enabledKeys = config.loadEnabledProjects();
//
//        final Set<Project> projects = new TreeSet<Project>(new Comparator<Project>() {
//            public int compare(Project p1, Project p2) {
//                return p1.getKey().compareTo(p2.getKey());
//            }
//        });
//        for (ProjectData p : projectService.getAllProjects()) {
//            projects.add(new Project(p.getId(), p.getKey(), p.getName(), p.getDefaultModerator(), enabledKeys.contains(p.getKey())));
//        }
//        return projects;
        return Collections.emptySet();
    }

    /**
     * Stores the projects for which auto review creation is enabled.
     *
     * @param projects
     */
    private void storeProjects(Set<Project> projects) {

        final List<String> enabled = new ArrayList<String>();
        for (Project p : projects) {
            if (p.isEnabled()) {
                enabled.add(p.getKey());
            }
        }
//        config.storeEnabledProjects(enabled);
    }
}
