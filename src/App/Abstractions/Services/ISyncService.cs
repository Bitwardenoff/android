﻿using System;
using System.Threading.Tasks;

namespace Bit.App.Abstractions
{
    public interface ISyncService
    {
        bool SyncInProgress { get; }
        Task<bool> SyncAsync(string id);
        Task<bool> SyncDeleteFolderAsync(string id, DateTime revisionDate);
        Task<bool> SyncDeleteLoginAsync(string id);
        Task<bool> FullSyncAsync(bool forceSync = false);
        Task<bool> FullSyncAsync(TimeSpan syncThreshold, bool forceSync = false);
    }
}
