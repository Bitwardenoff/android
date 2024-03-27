﻿using Bit.Core.Utilities.Fido2;

namespace Bit.Core.Abstractions
{
    public interface IFido2MakeCredentialConfirmationUserInterface : IFido2MakeCredentialUserInterface
    {
        /// <summary>
        /// Call this method after the use chose where to save the new Fido2 credential.
        /// </summary>
        /// <param name="cipherId">
        /// Cipher ID where to save the new credential.
        /// If <c>null</c> a new default passkey cipher item will be created
        /// </param>
        /// <param name="userVerified">
        /// Whether the user has been verified or not.
        /// If <c>null</c> verification has not taken place yet.
        /// </param>
        void Confirm(string cipherId, bool? userVerified);

        /// <summary>
        /// Cancels the current flow to make a credential
        /// </summary>
        void Cancel();

        /// <summary>
        /// Call this if an exception needs to happen on the credential making process
        /// </summary>
        void OnConfirmationException(Exception ex);

        Fido2UserVerificationOptions? GetCurrentUserVerificationOptions();
    }
}