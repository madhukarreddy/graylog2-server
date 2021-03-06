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
package org.graylog2.restclient.models.api.responses.dashboards;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * @author Lennart Koopmann <lennart@torch.sh>
 */
public class DashboardWidgetResponse {

    public String id;
    public String type;
    public String description;

    @JsonProperty("cache_time")
    public int cacheTime;

    @JsonProperty("creator_user_id")
    public String creatorUserId;

    public Map<String, Object> config;

}
