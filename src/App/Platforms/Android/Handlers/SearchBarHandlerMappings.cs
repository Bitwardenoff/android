﻿using Android.Views.InputMethods;

namespace Bit.App.Handlers
{
    public class SearchBarHandlerMappings
    {
        public static void Setup()
        {
            Microsoft.Maui.Handlers.SearchBarHandler.Mapper.AppendToMapping("CustomSearchBarHandler", (handler, searchBar) =>
            {
                try
                {
                    var magId = handler.PlatformView.Resources.GetIdentifier("android:id/search_mag_icon", null, null);
                    var magImage = (Android.Widget.ImageView)handler.PlatformView.FindViewById(magId);
                    magImage.LayoutParameters = new Android.Widget.LinearLayout.LayoutParams(0, 0);
                }
                catch { }
                handler.PlatformView.ImeOptions = handler.PlatformView.ImeOptions | (int)ImeFlags.NoPersonalizedLearning |
                    (int)ImeFlags.NoExtractUi;
            });
        }
    }
}
