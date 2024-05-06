﻿using System;
using System.Linq;
using System.Reflection;
using Bit.Core.Resources.Localization;
using Bit.Core.Attributes;
using CommunityToolkit.Maui.Converters;

namespace Bit.App.Utilities
{
    public static class EnumHelper
    {
        public static string GetLocalizedValue<T>(T value)
        {
            return GetLocalizedValue(value, typeof(T));
        }

        public static string GetLocalizedValue(object value, Type type)
        {
            if (!type.GetTypeInfo().IsEnum)
            {
                throw new ArgumentException("type should be an enum");
            }

            var valueMemberInfo = type.GetMember(value.ToString())[0];
            if (valueMemberInfo.GetCustomAttribute<LocalizableEnumAttribute>() is LocalizableEnumAttribute attr)
            {
                return AppResources.ResourceManager.GetString(attr.Key);
            }

            return value.ToString();
        }
    }
}
