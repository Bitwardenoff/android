package com.x8bit.bitwarden.data.auth.datasource.network.api

import com.x8bit.bitwarden.data.auth.datasource.network.model.OrganizationAutoEnrollStatusResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.OrganizationKeysResponseJson
import com.x8bit.bitwarden.data.auth.datasource.network.model.OrganizationResetPasswordEnrollRequestJson
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Defines raw calls under the authenticated /organizations API.
 */
interface AuthenticatedOrganizationApi {
    /**
     * Enrolls this user in the organization's password reset.
     */
    @PUT("/organizations/{orgId}/users/{userId}/reset-password-enrollment")
    suspend fun organizationResetPasswordEnroll(
        @Path("orgId") organizationId: String,
        @Path("userId") userId: String,
        @Body body: OrganizationResetPasswordEnrollRequestJson,
    ): Result<Unit>

    /**
     * Checks whether this organization auto enrolls users in password reset.
     */
    @GET("/organizations/{identifier}/auto-enroll-status")
    suspend fun getOrganizationAutoEnrollResponse(
        @Path("identifier") organizationIdentifier: String,
    ): Result<OrganizationAutoEnrollStatusResponseJson>

    /**
     * Gets the public and private keys for this organization.
     */
    @GET("/organizations/{id}/keys")
    suspend fun getOrganizationKeys(
        @Path("id") organizationId: String,
    ): Result<OrganizationKeysResponseJson>

    /**
     * Leaves the this organization.
     */
    @POST("/organizations/{id}/leave")
    suspend fun leaveOrganization(
        @Path("id") organizationId: String,
    ): Result<Unit>
}
