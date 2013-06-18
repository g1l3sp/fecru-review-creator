package org.kuali.crucible.plugin.reviewgenerator;

import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import org.junit.Assert;

public class UtilsTest {

    @Test
    public void testGetFirstNonEmptyLine() throws Exception {

        Assert.assertNull(Utils.firstNonEmptyLine(null));
        Assert.assertEquals("", Utils.firstNonEmptyLine(""));
        Assert.assertEquals("", Utils.firstNonEmptyLine("\r\n\n\r"));
        Assert.assertEquals(" line2 ", Utils.firstNonEmptyLine(" \n line2 "));
        Assert.assertEquals("line1", Utils.firstNonEmptyLine("\nline1\nline2"));
        Assert.assertEquals("line3", Utils.firstNonEmptyLine(" \n \nline3"));
    }

    @Test
    public void testExtractReviewIds() {

        Assert.assertTrue(Utils.extractReviewIds(null, "Foo").isEmpty());
        Assert.assertTrue(Utils.extractReviewIds("", "Foo").isEmpty());
        Assert.assertTrue(Utils.extractReviewIds("Foo", "Foo").isEmpty());
        Assert.assertTrue(Utils.extractReviewIds("This is not a review id: Foo-", "Foo").isEmpty());
        Assert.assertTrue(Utils.extractReviewIds("This is a review in a different project: CR-FE-1", "CR").isEmpty());

        Assert.assertEquals(ImmutableSet.of("FOO-1"), Utils.extractReviewIds("FOO-1", "FOO"));
        Assert.assertEquals(ImmutableSet.of("FOO-1", "FOO-3456"), Utils.extractReviewIds("There's 2 reviews in here: FOO-1, FOOBAR-4, FOO-3456", "FOO"));
    }
}
