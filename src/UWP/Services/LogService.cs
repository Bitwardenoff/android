﻿using Bit.App.Abstractions;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace Bit.UWP.Services
{
    public class LogService : ILogService
    {
        public void WriteLine(string message)
        {
            Debug.WriteLine(message);
        }
    }
}
