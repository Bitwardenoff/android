﻿using Bit.App.Abstractions;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Bit.UWP.Services
{
    public class GoogleAnalyticsService : IGoogleAnalyticsService
    {
        public void Dispatch(Action completionHandler = null)
        {

        }

        public void SetAppOptOut(bool optOut)
        {

        }

        public void TrackAppEvent(string eventName, string label = null)
        {

        }

        public void TrackEvent(string category, string eventName, string label = null)
        {

        }

        public void TrackException(string message, bool fatal)
        {
        }

        public void TrackExtensionEvent(string eventName, string label = null)
        {

        }

        public void TrackPage(string pageName)
        {
        }
    }
}
