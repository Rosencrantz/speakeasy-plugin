package com.atlassian.labs.speakeasy.rest;

import com.atlassian.labs.speakeasy.SpeakeasyManager;
import com.atlassian.labs.speakeasy.UnauthorizedAccessException;
import com.atlassian.labs.speakeasy.model.UserPlugins;
import com.atlassian.sal.api.user.UserManager;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.util.List;

import static java.util.Arrays.asList;

/**
 *
 */
@Path("/user")
public class UserResource
{
    private final SpeakeasyManager speakeasyManager;
    private final UserManager userManager;

    public UserResource(SpeakeasyManager speakeasyManager, UserManager userManager)
    {
        this.speakeasyManager = speakeasyManager;
        this.userManager = userManager;
    }

    @GET
    @Path("")
    @Produces("application/json")
    public Response getPlugins() throws UnauthorizedAccessException
    {
        return Response.ok(speakeasyManager.getRemotePluginList(userManager.getRemoteUsername())).build();
    }

    @PUT
    @Path("{pluginKey}")
    @Produces("application/json")
    public Response enableAccess(@PathParam("pluginKey") String pluginKey) throws UnauthorizedAccessException
    {
        String user = userManager.getRemoteUsername();
        List<String> affectedKeys = speakeasyManager.allowUserAccess(pluginKey, user);
        UserPlugins entity = speakeasyManager.getRemotePluginList(user, pluginKey);
        entity.setUpdated(affectedKeys);
        return Response.ok().entity(entity).build();
    }

    @DELETE
    @Path("{pluginKey}")
    @Produces("application/json")
    public Response disableAccess(@PathParam("pluginKey") String pluginKey) throws UnauthorizedAccessException
    {
        String user = userManager.getRemoteUsername();
        String affectedKey = speakeasyManager.disallowUserAccess(pluginKey, user);

        UserPlugins entity = speakeasyManager.getRemotePluginList(user, pluginKey);
        if (affectedKey != null)
        {
            entity.setUpdated(asList(affectedKey));
        }
        return Response.ok().entity(entity).build();
    }
}
