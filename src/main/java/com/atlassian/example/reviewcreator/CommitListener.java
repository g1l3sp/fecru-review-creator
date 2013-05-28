package com.atlassian.example.reviewcreator;

import com.atlassian.crucible.spi.PermId;
import com.atlassian.crucible.spi.data.*;
import com.atlassian.crucible.spi.services.*;
import com.atlassian.event.Event;
import com.atlassian.event.EventListener;
import com.atlassian.example.reviewcreator.*;
import com.atlassian.fisheye.event.CommitEvent;
import com.atlassian.fisheye.spi.data.ChangesetDataFE;
import com.atlassian.fisheye.spi.data.FileRevisionKeyData;
import com.atlassian.fisheye.spi.services.RevisionDataService;
import com.atlassian.sal.api.user.UserManager;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * <p>
 * Event listener that subscribes to commit events and creates a review for
 * each commit.
 * </p>
 * <p>
 * Auto review creation can be enabled/disabled by an administrator on a
 * per-project basis. Enabled projects must be bound to a FishEye repository
 * and must have a default Moderator configured in the admin section.
 * </p>
 * <p>
 * When auto review creation is enabled for a Crucible project, this
 * {@link com.atlassian.event.EventListener} will intercept all commits for
 * the project's repository and create a review for it. The review's author
 * role is set to the committer of the changeset and the review's moderator is
 * set to the project's default moderator.
 * </p>
 * <p>
 * When the project has default reviewers configured, these will be added to
 * the review.
 * </p>
 *
 * @author  Erik van Zijst
 */
public class CommitListener implements EventListener {

    private final Logger logger = Logger.getLogger(getClass().getName());

    private final RevisionDataService revisionService;          // provided by FishEye
    private final ReviewService reviewService;                  // provided by Crucible
    private final ProjectService projectService;                // provided by Crucible
    private final UserService userService;                      // provided by Crucible
    private final UserManager userManager;                      // provided by SAL
    private final ImpersonationService impersonator;            // provided by Crucible
    private final ConfigurationManager config;                  // provided by our plugin

    private static final ThreadLocal<Map<String, UserData>> committerToCrucibleUser =
            new ThreadLocal();

    public CommitListener(ConfigurationManager config,
                          ReviewService reviewService,
                          ProjectService projectService,
                          RevisionDataService revisionService,
                          UserService userService,
                          UserManager userManager,
                          ImpersonationService impersonator) {

        this.reviewService = reviewService;
        this.revisionService = revisionService;
        this.projectService = projectService;
        this.userService = userService;
        this.userManager = userManager;
        this.impersonator = impersonator;
        this.config = config;
    }

    public Class[] getHandledEventClasses() {
        return new Class[] {CommitEvent.class};
    }

    public void handleEvent(Event event) {

        final CommitEvent commit = (CommitEvent) event;

        if (isPluginEnabled()) {
            try {

                // switch to admin user so we can access all projects and API services:
                impersonator.doAsUser(null, config.loadRunAsUser(), new CommitHandler(commit));
            } catch (Exception e) {
                logger.error(String.format("Unable to auto-create " +
                        "review for changeset %s: %s.",
                        commit.getChangeSetId(), e.getMessage()), e);
            }
        }
    }


    /**
     * TODO: doc me
     */
    public class CommitHandler implements Operation<Void, ServerException> {

        private final CommitEvent commit;
        private final Map<String,ExpressionReviewConfig> expressionConfigMap;
        private final ChangesetDataFE changeSet;
        private final Map<String, ProjectData> projectsByKey;

        // lame optimization used to reduce expensive fetching of user data
        private String mruRepoKey = null;

        public CommitHandler(CommitEvent commit) {
            this.commit = commit;
            expressionConfigMap = config.loadExpressionConfigMap(); // Never null
            changeSet = revisionService.getChangeset(
                    commit.getRepositoryName(), commit.getChangeSetId());

            // get the projects that we might need
            projectsByKey = getEnabledProjectsMap(
                    commit.getRepositoryName(), expressionConfigMap);

        }

        public Void perform() throws ServerException {
            for (Map.Entry<String,ExpressionReviewConfig> expressionConfigEntry : expressionConfigMap.entrySet()) {
                // We do not want to create reviews using the prototype config
                if (!ConfigurationManager.CONFIG_PROTOTYPE_KEY.equals(expressionConfigEntry.getKey())) {
                    ExpressionReviewConfig expressionReviewConfig = expressionConfigEntry.getValue();
                    createReviewIfMatch(expressionReviewConfig);
                }
            }

            return null;
        }

        private void createReviewIfMatch(ExpressionReviewConfig expressionReviewConfig) throws ServerException {
            List<String> enabledForProjectKeys =
                    Utils.firstNotNull(expressionReviewConfig.getEnabledForProjectKeys(), Collections.<String>emptyList());

            // check that each ExpressionReviewConfig has a project in this repo before testing against it

            ProjectData firstMatchedProjectInRepo = null;
            // get the first relevant project from the config
            for (String projectKey : enabledForProjectKeys) {
                if (projectsByKey.containsKey(projectKey)) {
                    firstMatchedProjectInRepo = projectsByKey.get(projectKey);
                    break;
                }
            }

            if (firstMatchedProjectInRepo != null) {
                List<FileRevisionKeyData> fileRevisionKeyData = changeSet.getFileRevisions();

                // test against the ExpressionReviewConfig
                List<String> expressionMatches = determineExpressionMatches(fileRevisionKeyData, expressionReviewConfig);

                if (expressionMatches.size() > 0) { // vvv do we use this mapping anywhere?
                    String repoKey = firstMatchedProjectInRepo.getDefaultRepositoryName();

                    // loading the committer mappings is heavyweight, so don't re-load them for the same repo twice
                    // in a row
                    if (!repoKey.equals(mruRepoKey)) {
                        mruRepoKey = repoKey;
                        committerToCrucibleUser.set(loadCommitterMappings(repoKey));
                    }

                    createReview(commit.getRepositoryName(), changeSet, firstMatchedProjectInRepo, expressionReviewConfig, expressionMatches);
                } else {
                    logger.info(String.format("Not creating a review for changeset %s.",
                            commit.getChangeSetId()));
                }
            }
        }

        /**
         * Find any files that are matched by this expressionReviewConfig
         */
        private List<String> determineExpressionMatches(List<FileRevisionKeyData> fileRevisionKeyData,
                                                        ExpressionReviewConfig expressionReviewConfig) {
            List<String> expressionMatches = new ArrayList<String>();
            for (FileRevisionKeyData fileRevisionKeyDatum : fileRevisionKeyData) {
                String filePath = fileRevisionKeyDatum.getPath();
                boolean enabledForBranch = isEnabledForPathPrefix(expressionReviewConfig, filePath);

                for (String expression : expressionReviewConfig.getFileNameExpressions()) {

                    if (enabledForBranch && Pattern.matches(expression, filePath)) {
                        expressionMatches.add(expression +" : "+ filePath );
                    }
                }
            }
            return expressionMatches;
        }

        private boolean isEnabledForPathPrefix(ExpressionReviewConfig expressionReviewConfig, String filePath) {
            // check that the file is in an enabled branch
            boolean enabledForPathPrefix = false;
            List<String> enabledForBranchPrefixes = expressionReviewConfig.getEnabledForPathPrefixes();
            for (String enabledForBranchPrefix : enabledForBranchPrefixes) {
                if (filePath.startsWith(enabledForBranchPrefix)) {
                    enabledForPathPrefix = true;
                    break;
                }
            }
            return enabledForPathPrefix;
        }

    } // End commit handler inner class


    // Not used, but similar functionality may be wanted some day
//    /**
//     * Attempts to add the change set to an existing open review by scanning
//     * the commit message for review IDs in the current project. When multiple
//     * IDs are found, the first non-closed review is used.
//     *
//     * @param repoKey
//     * @param cs
//     * @param project
//     * @return  {@code true} if the change set was successfully added to an
//     * existing review, {@code false} otherwise.
//     */
//    private boolean appendToReview(final String repoKey, final ChangesetDataFE cs, final ProjectData project) {
//
//        final ReviewData review = getFirstOpenReview(Utils.extractReviewIds(cs.getComment(), project.getKey()));
//
//        if (review != null) {
//
//            // impersonate the review's moderator (or creator if there is no moderator set):
//            return impersonator.doAsUser(null,
//                    Utils.defaultIfNull(review.getModerator(), review.getCreator()).getUserName(),
//                    new Operation<Boolean, RuntimeException>() {
//
//                        public Boolean perform() throws RuntimeException {
//
//                            try {
//                                reviewService.addChangesetsToReview(review.getPermaId(), repoKey, Collections.singletonList(new ChangesetData(cs.getCsid())));
//                                addComment(review, String.format(
//                                        "The Automatic Review Creator Plugin added changeset {cs:id=%s|rep=%s} to this review.",
//                                        cs.getCsid(), repoKey));
//                                return true;
//                            } catch (Exception e) {
//                                logger.warn(String.format("Error appending changeset %s to review %s: %s",
//                                        cs.getCsid(), review.getPermaId().getId(), e.getMessage()), e);
//                            }
//                            return false;
//                        }
//                    });
//        }
//        return false;
//    }

    // not used
//    /**
//     * Note that this check is broken in Crucible older than 2.2. In 2.1, the
//     * review state gets stale and won't always show the current state.
//     * See: http://jira.atlassian.com/browse/CRUC-2912
//     *
//     * @param reviewIds
//     * @return
//     */
//    private ReviewData getFirstOpenReview(Iterable<String> reviewIds) {
//
//        final Collection<ReviewData.State> acceptableStates = ImmutableSet.of(
//                ReviewData.State.Draft,
//                ReviewData.State.Approval,
//                ReviewData.State.Review);
//
//        for (String reviewId : reviewIds) {
//            try {
//                final ReviewData review = reviewService.getReview(new PermId<ReviewData>(reviewId), false);
//                if (acceptableStates.contains(review.getState())) {
//                    return review;
//                }
//            } catch (NotFoundException nfe) {
//                /* Exceptions for flow control is bad practice, but the API
//                 * has no exists() method.
//                 */
//            }
//        }
//        return null;
//    }

    private void createReview(final String repoKey, final ChangesetDataFE cs,
                              final ProjectData project, final ExpressionReviewConfig expressionReviewConfig,
                              List<String> expressionMatches)
            throws ServerException {

        final ReviewData template = buildReviewTemplate(cs, project, expressionReviewConfig, expressionMatches);

        // switch to user moderator:
        impersonator.doAsUser(null, template.getModerator().getUserName(), new Operation<Void, ServerException>() {
            public Void perform() throws ServerException {

                // create a new review:
                final ReviewData review = reviewService.createReviewFromChangeSets(
                        template,
                        repoKey,
                        Collections.singletonList(new ChangesetData(cs.getCsid())));

                // add the project's default reviewers:
                addReviewers(review, expressionReviewConfig);

                // start the review, so everyone is notified:
                reviewService.changeState(review.getPermaId(), "action:approveReview");
                addComment(review, "This review was created by the Automatic Review Creator Plugin.");

                logger.info(String.format("Auto-created review %s for " +
                        "commit %s:%s with moderator %s.",
                        review.getPermaId(), repoKey,
                        cs.getCsid(), review.getModerator().getUserName()));
                return null;
            }
        });
    }

    /**
     * Must be called within the context of a user.
     *
     * @param review
     * @param message
     */
    private void addComment(final ReviewData review, final String message) {

        final GeneralCommentData comment = new GeneralCommentData();
        comment.setCreateDate(new Date());
        comment.setDraft(false);
        comment.setDeleted(false);
        comment.setMessage(message);

        try {
            reviewService.addGeneralComment(review.getPermaId(), comment);
        } catch (Exception e) {
            logger.error(String.format("Unable to add a general comment to review %s: %s",
                    review.getPermaId().getId(), e.getMessage()), e);
        }
    }

    private void addReviewers(ReviewData review, ExpressionReviewConfig expressionReviewConfig) {
        final List<String> reviewers = expressionReviewConfig.getUserReviewers();
        List<String> validatedReviewers = new ArrayList<String>(reviewers.size());
        // filter out invalid users
        for (String reviewer : reviewers) {
            // not sure if the service will puke on invalid users, or just return null.
            try {
                UserData user = userService.getUser(reviewer);
                if (user != null &&
                        !user.equals(review.getModerator()) &&
                        !user.equals(review.getAuthor())) {
                    // it's invalid for the author or moderator to also be a reviewer
                    validatedReviewers.add(reviewer);
                }
            } catch (ServerException e) {
                // assume this means an invalid user
            }
        }
        if (validatedReviewers != null && !validatedReviewers.isEmpty()) {
            reviewService.addReviewers(review.getPermaId(),
                    validatedReviewers.toArray(new String[validatedReviewers.size()]));
        }
    }

    private ReviewData buildReviewTemplate(ChangesetDataFE cs, ProjectData project, ExpressionReviewConfig expressionReviewConfig, List<String> expressionMatches)
            throws ServerException {

        final UserData creator = committerToCrucibleUser.get().get(cs.getAuthor()) == null ?
                userService.getUser(project.getDefaultModerator()) :
                committerToCrucibleUser.get().get(cs.getAuthor());
        final Date dueDate = project.getDefaultDuration() == null ? null :
                DateHelper.addWorkingDays(new Date(), project.getDefaultDuration());

        //cs.getAuthor();

        String reviewName = expressionReviewConfig.getReviewSubjectPrefix() +" ("+ Utils.firstNonEmptyLine(cs.getComment()) +")";

        StringBuilder reviewDescriptionBuilder = new StringBuilder();
        reviewDescriptionBuilder.append(StringUtils.defaultIfEmpty(expressionReviewConfig.getReviewDescription(), cs.getComment()));
        reviewDescriptionBuilder.append("\n\n");
        reviewDescriptionBuilder.append("EXPRESSION : MATCHED FILE\n\n");
        for (String expressionMatch : expressionMatches) {
            reviewDescriptionBuilder.append(expressionMatch);
            reviewDescriptionBuilder.append('\n');
        }

        UserData moderator = creator;
        if (!StringUtils.isEmpty(project.getDefaultModerator())) {
            moderator = userService.getUser(project.getDefaultModerator());
        }

        return new ReviewDataBuilder()
                .setProjectKey(project.getKey())
                .setName(reviewName)
                .setDescription(reviewDescriptionBuilder.toString())
                .setAuthor(creator)
                .setModerator(moderator)
                .setCreator(creator)
                        // TODO: try this again?
//                .setAllowReviewersToJoin(project.isAllowReviewersToJoin())  // << this caused an exception in Atlassian code :-(
                .setDueDate(dueDate)
                .build();
    }


    /**
     * <p>
     * Given a FishEye repository key, returns a Map whose values are all the Crucible projects that
     * have expression reviews enabled for them, and whose keys are the project keys.
     * </p>
     * <p>
     * This method must be invoked with admin permissions.
     * </p>
     *
     * @param repoKey a FishEye repository key (e.g. "CR").
     * @param expressionReviewConfigMap the map of {@link com.atlassian.example.reviewcreator.ExpressionReviewConfig}s
     * @return  A map from project key to {@link com.atlassian.crucible.spi.data.ProjectData} for all "enabled" projects
     * within the repository with the given key.
     */
    private Map<String, ProjectData> getEnabledProjectsMap(String repoKey, Map<String, ExpressionReviewConfig> expressionReviewConfigMap) {
        Map<String, ProjectData> results = new HashMap<String, ProjectData>();

        final List<ProjectData> allProjects = projectService.getAllProjects();
        final Set<String> enabled = new HashSet<String>();

        for (Map.Entry<String,ExpressionReviewConfig> expressionReviewConfigEntry : expressionReviewConfigMap.entrySet() ) {
            // ignore the config prototype settings, it shouldn't be considered since it is only used as a template
            if (!ConfigurationManager.CONFIG_PROTOTYPE_KEY.equals(expressionReviewConfigEntry.getKey())) {
                ExpressionReviewConfig expressionReviewConfig = expressionReviewConfigEntry.getValue();
                List<String> enabledForProjectKeys = expressionReviewConfig.getEnabledForProjectKeys();

                if (enabledForProjectKeys != null) { enabled.addAll(enabledForProjectKeys); }
            }
        }

        for (ProjectData project : allProjects) {
            if (repoKey.equals(project.getDefaultRepositoryName()) &&
                    enabled.contains(project.getKey())) {
                results.put(project.getKey(), project);
            }
        }
        return results;
    }

    /**
     * Returns a map containing all committer names that are mapped to Crucible
     * user accounts.
     * This is an expensive operation that will be redundant when the fecru SPI
     * gets a <code>CommitterMapperService</code>.
     * <p>
     * This method must be invoked with admin permissions.
     * </p>
     *
     * @param   repoKey
     * @return
     */
    private Map<String, UserData> loadCommitterMappings(final String repoKey)
            throws ServerException {

        final Map<String, UserData> committerToUser = new HashMap<String, UserData>();
        for (UserData ud : userService.getAllUsers()) {
            final UserProfileData profile = userService.getUserProfile(ud.getUserName());
            final List<String> committers = profile.getMappedCommitters().get(repoKey);
            if (committers != null) {
                for (String committer : committers) {
                    committerToUser.put(committer, ud);
                }
            }
        }
        return committerToUser;
    }

    private boolean isPluginEnabled() {
        return !StringUtils.isEmpty(config.loadRunAsUser());
    }

}
