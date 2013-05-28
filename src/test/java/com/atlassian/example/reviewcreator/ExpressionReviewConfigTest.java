package com.atlassian.example.reviewcreator;

import net.sf.json.JSON;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Created with IntelliJ IDEA.
 * User: gilesp
 * Date: 31/01/13
 * Time: 10:44 PM
 * To change this template use File | Settings | File Templates.
 */
public class ExpressionReviewConfigTest {

    ExpressionReviewConfig buildReviewTypeConfig1() {
        ExpressionReviewConfig erc = new ExpressionReviewConfig();

        erc.setReviewSubjectPrefix("Code Review Subject for Config1");
        erc.setReviewDescription(
                "This is a description of why this review was \n" +
                        "generated. This particular one was created \n" +
                        "because of a test for Config1");

        erc.setFileNameExpressions(Arrays.asList(new String[]{
                "/home/gilesp/foo/.*",
                "/home/gilesp/bar/.*",
                "/home/gilesp/baz/.*",
                "/home/gilesp/fig/.*"
        }));

        erc.setEnabledForPathPrefixes(Arrays.asList(new String[]{
                "branches/rice-",
                "trunk/"
        }));

        erc.setEnabledForProjectKeys(Arrays.asList(new String[]{
                "rice",
                "kfs",
                "kra",
                "kpme"
        }));

        erc.setUserReviewers(Arrays.asList(new String[]{
                "gilesp",
                "ewestfal",
                "cniesen",
                "jkneal"
        }));

        erc.setGroupReviewers(Arrays.asList(new String[]{
                "RiceDevLeads",
                "KTI"
        }));

        return erc;
    }

    @Test
    public void testJsonSerialization() {
        ExpressionReviewConfig erc1 = buildReviewTypeConfig1();

        JSON erc1Json = JSONSerializer.toJSON(erc1);
        String erc1JsonString = erc1Json.toString(2);

        System.out.println(erc1JsonString);

        JSONObject erc1JsonObject = JSONObject.fromObject(erc1JsonString);
        ExpressionReviewConfig erc1Reconstituted = (ExpressionReviewConfig)erc1JsonObject.toBean(ExpressionReviewConfig.class);

//        ExpressionReviewConfig erc1Reconstituted = (ExpressionReviewConfig)JSONSerializer.toJava(erc1JsonObject);

        assertEquals(erc1, erc1Reconstituted);
    }

    @Test
    public void testValidate() throws Exception {
        ExpressionReviewConfig erc1 = buildReviewTypeConfig1();

        // should be valid out of the box
        erc1.validateConfig();

        // must have a subject
        erc1 = buildReviewTypeConfig1(); // reset the config
        erc1.setReviewSubjectPrefix(null);
        try {
            erc1.validateConfig();
            fail("config should be invalid without a reviewSubject");
        } catch (ExpressionReviewConfig.ReviewValidationException rve) {
            // guud
        }

        // expressions must be valid
        erc1 = buildReviewTypeConfig1(); // reset the config
        erc1.setFileNameExpressions(Arrays.asList("asdf", "[" /* <- invalid */));
        try {
            erc1.validateConfig();
            fail("config should be invalid with that '[' expression");
        } catch (ExpressionReviewConfig.ReviewValidationException rve) {
            // guud
        }

        // must have a reviewer of some type
        erc1 = buildReviewTypeConfig1(); // reset the config
        erc1.setUserReviewers(Collections.<String>emptyList());
        erc1.setGroupReviewers(Collections.<String>emptyList());
        try {
            erc1.validateConfig();
            fail("config should be invalid without any reviewers");
        } catch (ExpressionReviewConfig.ReviewValidationException rve) {
            // guud
        }
    }

}
