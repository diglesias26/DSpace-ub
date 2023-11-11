/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.repository;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;

import org.dspace.app.rest.Parameter;
import org.dspace.app.rest.SearchRestMethod;
import org.dspace.app.rest.exception.RepositoryMethodNotImplementedException;
import org.dspace.app.rest.model.QAEventRest;
import org.dspace.app.rest.model.patch.Patch;
import org.dspace.app.rest.repository.patch.ResourcePatch;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Item;
import org.dspace.content.QAEvent;
import org.dspace.content.service.ItemService;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.qaevent.dao.QAEventsDao;
import org.dspace.qaevent.service.QAEventService;
import org.dspace.util.UUIDUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

/**
 * Rest repository that handle QA events.
 *
 * @author Andrea Bollini (andrea.bollini at 4science.it)
 *
 */
@Component(QAEventRest.CATEGORY + "." + QAEventRest.NAME)
public class QAEventRestRepository extends DSpaceRestRepository<QAEventRest, String> {

    final static String ORDER_FIELD = "trust";

    @Autowired
    private QAEventService qaEventService;

    @Autowired
    private QAEventsDao qaEventDao;

    @Autowired
    private ItemService itemService;

    @Autowired
    private ResourcePatch<QAEvent> resourcePatch;

    @Override
    @PreAuthorize("hasPermission(#id, 'QUALITYASSURANCEEVENT', 'READ')")
    public QAEventRest findOne(Context context, String id) {
        QAEvent qaEvent = qaEventService.findEventByEventId(context, id);
        if (qaEvent == null) {
            // check if this request is part of a patch flow
            qaEvent = (QAEvent) requestService.getCurrentRequest().getAttribute("patchedNotificationEvent");
            if (qaEvent != null && qaEvent.getEventId().contentEquals(id)) {
                return converter.toRest(qaEvent, utils.obtainProjection());
            } else {
                return null;
            }
        }
        return converter.toRest(qaEvent, utils.obtainProjection());
    }

    @SearchRestMethod(name = "findByTopic")
    @PreAuthorize("hasAuthority('AUTHENTICATED')")
    public Page<QAEventRest> findByTopic(@Parameter(value = "topic", required = true) String topic,
        Pageable pageable) {
        Context context = obtainContext();
        String[] topicIdSplitted = topic.split(":", 3);
        if (topicIdSplitted.length < 2) {
            return null;
        }
        String sourceName = topicIdSplitted[0];
        String topicName = topicIdSplitted[1].replaceAll("!", "/");
        UUID target = topicIdSplitted.length == 3 ? UUID.fromString(topicIdSplitted[2]) : null;
        List<QAEvent> qaEvents = null;
        long count = 0L;
        qaEvents = qaEventService.findEventsByTopicAndPageAndTarget(context, sourceName, topicName,
            pageable.getOffset(), pageable.getPageSize(), target);
        count = qaEventService.countEventsByTopicAndTarget(context, sourceName, topicName, target);
        if (qaEvents == null) {
            return null;
        }
        return converter.toRestPage(qaEvents, pageable, count, utils.obtainProjection());
    }

    @Override
    @PreAuthorize("hasPermission(#id, 'QUALITYASSURANCEEVENT', 'DELETE')")
    protected void delete(Context context, String eventId) throws AuthorizeException {
        Item item = findTargetItem(context, eventId);
        EPerson eperson = context.getCurrentUser();
        qaEventService.deleteEventByEventId(eventId);
        qaEventDao.storeEvent(context, eventId, eperson, item);
    }

    @Override
    public Page<QAEventRest> findAll(Context context, Pageable pageable) {
        throw new RepositoryMethodNotImplementedException(QAEventRest.NAME, "findAll");
    }

    @Override
    @PreAuthorize("hasPermission(#id, 'QUALITYASSURANCEEVENT', 'WRITE')")
    protected void patch(Context context, HttpServletRequest request, String apiCategory, String model,
        String id, Patch patch) throws SQLException, AuthorizeException {
        QAEvent qaEvent = qaEventService.findEventByEventId(context, id);
        resourcePatch.patch(context, qaEvent, patch.getOperations());
    }

    private Item findTargetItem(Context context, String eventId) {
        QAEvent qaEvent = qaEventService.findEventByEventId(context, eventId);
        if (qaEvent == null) {
            return null;
        }

        try {
            return itemService.find(context, UUIDUtils.fromString(qaEvent.getTarget()));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Class<QAEventRest> getDomainClass() {
        return QAEventRest.class;
    }

}
