﻿#if !FDROID
using System;
using System.Threading.Tasks;
using Android.App;
using Android.Content;
using Android.OS;
using AndroidX.Core.App;
using Bit.App.Abstractions;
using Bit.Core;
using Bit.Core.Abstractions;
using Bit.Droid.Utilities;
using Xamarin.Forms;

namespace Bit.Droid.Services
{
    public class AndroidPushNotificationService : IPushNotificationService
    {
        private readonly IStateService _stateService;
        private readonly IPushNotificationListenerService _pushNotificationListenerService;

        public AndroidPushNotificationService(
            IStateService stateService,
            IPushNotificationListenerService pushNotificationListenerService)
        {
            _stateService = stateService;
            _pushNotificationListenerService = pushNotificationListenerService;
        }

        public bool IsRegisteredForPush => NotificationManagerCompat.From(Android.App.Application.Context)?.AreNotificationsEnabled() ?? false;

        public Task<bool> AreNotificationsSettingsEnabledAsync()
        {
            return Task.FromResult(IsRegisteredForPush);
        }

        public async Task<string> GetTokenAsync()
        {
            return await _stateService.GetPushCurrentTokenAsync();
        }

        public async Task RegisterAsync()
        {
            var registeredToken = await _stateService.GetPushRegisteredTokenAsync();
            var currentToken = await GetTokenAsync();
            if (!string.IsNullOrWhiteSpace(registeredToken) && registeredToken != currentToken)
            {
                await _pushNotificationListenerService.OnRegisteredAsync(registeredToken, Device.Android);
            }
            else
            {
                await _stateService.SetPushLastRegistrationDateAsync(DateTime.UtcNow);
            }
        }

        public Task UnregisterAsync()
        {
            // Do we ever need to unregister?
            return Task.FromResult(0);
        }

        public void DismissLocalNotification(string notificationId)
        {
            if (int.TryParse(notificationId, out int intNotificationId))
            {
                var notificationManager = NotificationManagerCompat.From(Android.App.Application.Context);
                notificationManager.Cancel(intNotificationId);
            }
        }

        public void SendLocalNotification(string title, string message, string notificationId)
        {
            if (string.IsNullOrEmpty(notificationId))
            {
                throw new ArgumentNullException("notificationId cannot be null or empty.");
            }
            
            var context = Android.App.Application.Context;
            var intent = new Intent(context, typeof(MainActivity));
            var pendingIntentFlags = AndroidHelpers.AddPendingIntentMutabilityFlag(PendingIntentFlags.UpdateCurrent, true);
            var pendingIntent = PendingIntent.GetActivity(context, 20220801, intent, pendingIntentFlags);
            var builder = new NotificationCompat.Builder(context, Constants.AndroidNotificationChannelId)
               .SetContentIntent(pendingIntent)
               .SetContentTitle(title)
               .SetContentText(message)
               .SetTimeoutAfter(Constants.PasswordlessNotificationTimeoutInMinutes * 60000)
               .SetSmallIcon(Resource.Drawable.ic_notification)
               .SetColor((int)Android.Graphics.Color.White)
               .SetAutoCancel(true);

            var notificationManager = NotificationManagerCompat.From(context);
            notificationManager.Notify(int.Parse(notificationId), builder.Build());
        }
    }
}
#endif
