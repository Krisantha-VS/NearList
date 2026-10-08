package com.sharma.nearlist.domain;

import static org.junit.Assert.assertEquals;

import com.sharma.nearlist.data.entity.ActivityEvent;
import com.sharma.nearlist.data.model.Category;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;

public class ActivityCsvTest {

    private static final TimeZone COLOMBO = TimeZone.getTimeZone("Asia/Colombo");

    @Test
    public void build_writesHeaderAndOneLinePerEvent() {
        ActivityEvent enter = new ActivityEvent();
        enter.timestamp = 1_791_687_427_000L; // 2026-10-11 08:27:07 +05:30
        enter.type = ActivityEvent.TYPE_ENTER;
        enter.placeName = "Keells Kalutara";
        enter.category = Category.GROCERY;
        enter.outcome = ActivityEvent.OUTCOME_SUPPRESSED;
        enter.reason = "OUTSIDE_TIME_WINDOW";
        enter.detail = "6–9 PM";
        enter.taskCount = 2;

        ActivityEvent system = new ActivityEvent();
        system.timestamp = 1_791_687_427_000L;
        system.type = ActivityEvent.TYPE_SYSTEM;
        system.outcome = ActivityEvent.OUTCOME_INFO;
        system.detail = "Re-registered 3 zones after restart";

        String csv = ActivityCsv.build(Arrays.asList(enter, system), COLOMBO);

        assertEquals(ActivityCsv.HEADER + "\r\n"
                + "2026-10-11T08:27:07+05:30,ENTER,Keells Kalutara,GROCERY,SUPPRESSED,OUTSIDE_TIME_WINDOW,6–9 PM,2\r\n"
                + "2026-10-11T08:27:07+05:30,SYSTEM,,,INFO,,Re-registered 3 zones after restart,0\r\n", csv);
    }

    @Test
    public void build_withNoEvents_isJustTheHeader() {
        assertEquals(ActivityCsv.HEADER + "\r\n", ActivityCsv.build(Collections.emptyList(), COLOMBO));
    }

    @Test
    public void field_quotesCommasQuotesAndLineBreaks() {
        assertEquals("plain", ActivityCsv.field("plain"));
        assertEquals("", ActivityCsv.field(null));
        assertEquals("\"Cargills, Panadura\"", ActivityCsv.field("Cargills, Panadura"));
        assertEquals("\"the \"\"big\"\" one\"", ActivityCsv.field("the \"big\" one"));
        assertEquals("\"two\nlines\"", ActivityCsv.field("two\nlines"));
    }
}
