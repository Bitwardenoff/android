﻿using System;
using Bit.App.Abstractions;
using System.Text;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;

namespace Bit.App.Services
{
    public class TokenService : ITokenService
    {
        private const string TokenKey = "accessToken";
        private const string RefreshTokenKey = "refreshToken";
        private const string AuthBearerKey = "token";

        private readonly ISecureStorageService _secureStorage;

        private string _token;
        private dynamic _decodedToken;
        private string _refreshToken;
        private string _authBearer;

        private static readonly DateTime _epoc = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);

        public TokenService(ISecureStorageService secureStorage)
        {
            _secureStorage = secureStorage;
        }

        public string Token
        {
            get
            {
                if(_token != null)
                {
                    return _token;
                }

                var tokenBytes = _secureStorage.Retrieve(TokenKey);
                if(tokenBytes == null)
                {
                    return null;
                }

                _token = Encoding.UTF8.GetString(tokenBytes, 0, tokenBytes.Length);
                return _token;
            }
            set
            {
                if(value != null)
                {
                    var tokenBytes = Encoding.UTF8.GetBytes(value);
                    _secureStorage.Store(TokenKey, tokenBytes);
                }
                else
                {
                    _secureStorage.Delete(TokenKey);
                    RefreshToken = null;
                    AuthBearer = null;
                }

                _decodedToken = null;
                _token = value;
            }
        }

        public DateTime TokenExpiration
        {
            get
            {
                var decoded = DecodeToken();
                if(decoded?["exp"] == null)
                {
                    throw new InvalidOperationException("No exp in token.");
                }

                return _epoc.AddSeconds(Convert.ToDouble(decoded["exp"].Value<long>()));
            }
        }

        public bool TokenExpired => DateTime.UtcNow < TokenExpiration;
        public TimeSpan TokenTimeRemaining => TokenExpiration - DateTime.UtcNow;
        public bool TokenNeedsRefresh => TokenTimeRemaining.TotalMinutes < 5;
        public string TokenUserId => DecodeToken()?["sub"].Value<string>();
        public string TokenEmail => DecodeToken()?["email"].Value<string>();
        public string TokenName => DecodeToken()?["name"].Value<string>();

        public string RefreshToken
        {
            get
            {
                if(_refreshToken != null)
                {
                    return _refreshToken;
                }

                var tokenBytes = _secureStorage.Retrieve(RefreshTokenKey);
                if(tokenBytes == null)
                {
                    return null;
                }

                _refreshToken = Encoding.UTF8.GetString(tokenBytes, 0, tokenBytes.Length);
                return _refreshToken;
            }
            set
            {
                if(value != null)
                {
                    var tokenBytes = Encoding.UTF8.GetBytes(value);
                    _secureStorage.Store(RefreshTokenKey, tokenBytes);
                }
                else
                {
                    _secureStorage.Delete(RefreshTokenKey);
                }

                _refreshToken = value;
            }
        }

        public string AuthBearer
        {
            get
            {
                if(_authBearer != null)
                {
                    return _authBearer;
                }

                var tokenBytes = _secureStorage.Retrieve(AuthBearerKey);
                if(tokenBytes == null)
                {
                    return null;
                }

                _authBearer = Encoding.UTF8.GetString(tokenBytes, 0, tokenBytes.Length);
                return _authBearer;
            }
            set
            {
                if(value != null)
                {
                    var tokenBytes = Encoding.UTF8.GetBytes(value);
                    _secureStorage.Store(AuthBearerKey, tokenBytes);
                }
                else
                {
                    _secureStorage.Delete(AuthBearerKey);
                }

                _authBearer = value;
            }
        }

        public JObject DecodeToken()
        {
            if(_decodedToken != null)
            {
                return _decodedToken;
            }

            if(Token == null)
            {
                throw new InvalidOperationException($"{nameof(Token)} not found.");
            }

            var parts = Token.Split('.');
            if(parts.Length != 3)
            {
                throw new InvalidOperationException($"{nameof(Token)} must have 3 parts");
            }

            var decodedBytes = Base64UrlDecode(parts[1]);
            if(decodedBytes == null || decodedBytes.Length < 1)
            {
                throw new InvalidOperationException($"{nameof(Token)} must have 3 parts");
            }

            _decodedToken = JObject.Parse(Encoding.UTF8.GetString(decodedBytes, 0, decodedBytes.Length));
            return _decodedToken;
        }

        private static byte[] Base64UrlDecode(string input)
        {
            var output = input;
            // 62nd char of encoding
            output = output.Replace('-', '+');
            // 63rd char of encoding
            output = output.Replace('_', '/');
            // Pad with trailing '='s
            switch(output.Length % 4)
            {
                case 0:
                    // No pad chars in this case
                    break;
                case 2:
                    // Two pad chars
                    output += "=="; break;
                case 3:
                    // One pad char
                    output += "="; break;
                default:
                    throw new InvalidOperationException("Illegal base64url string!");
            }

            // Standard base64 decoder
            return Convert.FromBase64String(output);
        }
    }
}
