/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog2.rest.resources.system.logs;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog2.rest.models.system.loggers.responses.LoggersSummary;
import org.graylog2.rest.models.system.loggers.responses.SingleLoggerSummary;
import org.graylog2.rest.models.system.loggers.responses.SingleSubsystemSummary;
import org.graylog2.rest.models.system.loggers.responses.SubsystemSummary;
import org.graylog2.shared.rest.resources.RestResource;
import org.graylog2.shared.security.RestPermissions;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.Enumeration;
import java.util.Map;

@RequiresAuthentication
@Api(value = "System/Loggers", description = "Internal Graylog loggers")
@Path("/system/loggers")
public class LoggersResource extends RestResource {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(LoggersResource.class);

    private static final Map<String, Subsystem> SUBSYSTEMS = ImmutableMap.<String, Subsystem>of(
            "graylog2", new Subsystem("Graylog2", "org.graylog2", "All messages from graylog2-owned systems."),
            "indexer", new Subsystem("Indexer", "org.elasticsearch", "All messages related to indexing and searching."),
            "authentication", new Subsystem("Authentication", "org.apache.shiro", "All user authentication messages."),
            "sockets", new Subsystem("Sockets", "netty", "All messages related to socket communication."));

    @GET
    @Timed
    @ApiOperation(value = "List all loggers and their current levels")
    @Produces(MediaType.APPLICATION_JSON)
    public LoggersSummary loggers() {
        final Map<String, SingleLoggerSummary> loggerList = Maps.newHashMap();

        final Enumeration loggers = Logger.getRootLogger().getLoggerRepository().getCurrentLoggers();
        while (loggers.hasMoreElements()) {
            Logger logger = (Logger) loggers.nextElement();
            if (!isPermitted(RestPermissions.LOGGERS_READ, logger.getName())) {
                continue;
            }

            loggerList.put(logger.getName(), SingleLoggerSummary.create(logger.getEffectiveLevel().toString().toLowerCase(), logger.getEffectiveLevel().getSyslogEquivalent()));
        }

        return LoggersSummary.create(loggerList);
    }

    @GET
    @Timed
    @Path("/subsystems")
    @ApiOperation(value = "List all logger subsystems and their current levels")
    @Produces(MediaType.APPLICATION_JSON)
    public SubsystemSummary subsystems() {
        final Map<String, SingleSubsystemSummary> subsystems = Maps.newHashMap();
        for (Map.Entry<String, Subsystem> subsystem : SUBSYSTEMS.entrySet()) {
            if (!isPermitted(RestPermissions.LOGGERS_READSUBSYSTEM, subsystem.getKey())) {
                continue;
            }

            try {
                final Level effectiveLevel = Logger.getLogger(subsystem.getValue().getCategory()).getEffectiveLevel();

                subsystems.put(subsystem.getKey(), SingleSubsystemSummary.create(
                        subsystem.getValue().getTitle(),
                        subsystem.getValue().getCategory(),
                        subsystem.getValue().getDescription(),
                        effectiveLevel.toString().toLowerCase(),
                        effectiveLevel.getSyslogEquivalent()
                ));
            } catch (Exception e) {
                LOG.warn("Error while listing logger subsystem.", e);
            }
        }

        return SubsystemSummary.create(subsystems);
    }

    @PUT
    @Timed
    @ApiOperation(value = "Set the loglevel of a whole subsystem",
            notes = "Provided level is falling back to DEBUG if it does not exist")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "No such subsystem.")
    })
    @Path("/subsystems/{subsystem}/level/{level}")
    public void setSubsystemLoggerLevel(
            @ApiParam(name = "subsystem", required = true) @PathParam("subsystem") String subsystemTitle,
            @ApiParam(name = "level", required = true) @PathParam("level") String level) {
        if (!SUBSYSTEMS.containsKey(subsystemTitle)) {
            LOG.warn("No such subsystem: [{}]. Returning 404.", subsystemTitle);
            throw new NotFoundException();
        }
        checkPermission(RestPermissions.LOGGERS_EDITSUBSYSTEM, subsystemTitle);
        Subsystem subsystem = SUBSYSTEMS.get(subsystemTitle);

        // This is never null. Worst case is a logger that does not exist.
        Logger logger = Logger.getLogger(subsystem.getCategory());

        // Setting the level falls back to DEBUG if provided level is invalid.
        Level newLevel = Level.toLevel(level.toUpperCase());
        logger.setLevel(newLevel);
    }

    @PUT
    @Timed
    @ApiOperation(value = "Set the loglevel of a single logger",
            notes = "Provided level is falling back to DEBUG if it does not exist")
    @Path("/{loggerName}/level/{level}")
    public void setSingleLoggerLevel(
            @ApiParam(name = "loggerName", required = true) @PathParam("loggerName") String loggerName,
            @ApiParam(name = "level", required = true) @PathParam("level") String level) {
        checkPermission(RestPermissions.LOGGERS_EDIT, loggerName);
        // This is never null. Worst case is a logger that does not exist.
        Logger logger = Logger.getLogger(loggerName);

        // Setting the level falls back to DEBUG if provided level is invalid.
        Level newLevel = Level.toLevel(level.toUpperCase());
        logger.setLevel(newLevel);
    }

    private static class Subsystem {

        private final String title;
        private final String category;
        private final String description;

        public Subsystem(String title, String category, String description) {
            this.title = title;
            this.category = category;
            this.description = description;
        }

        private String getTitle() {
            return title;
        }

        private String getCategory() {
            return category;
        }

        private String getDescription() {
            return description;
        }
    }
}
