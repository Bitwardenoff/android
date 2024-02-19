﻿using Bit.App.Controls;
using Bit.App.Models;
using Bit.Core.Abstractions;
using Bit.Core.Models.View;
using Bit.Core.Resources.Localization;
using Bit.Core.Utilities;

namespace Bit.App.Pages
{
    public partial class CiphersPage : BaseContentPage
    {
        private readonly string _autofillUrl;
        private readonly IAutofillHandler _autofillHandler;

        private CiphersPageViewModel _vm;
        private bool _hasFocused;

        public CiphersPage(Func<CipherView, bool> filter,
            string pageTitle = null,
            string vaultFilterSelection = null,
            bool deleted = false,
            AppOptions appOptions = null)
        {
            InitializeComponent();
            _vm = BindingContext as CiphersPageViewModel;
            _vm.Page = this;
            _autofillUrl = appOptions?.Uri;
            _vm.Prepare(filter, deleted, appOptions);

            if (pageTitle != null)
            {
                _vm.PageTitle = string.Format(AppResources.SearchGroup, pageTitle);
            }
            else
            {
                _vm.PageTitle = AppResources.SearchVault;
            }
            _vm.VaultFilterDescription = vaultFilterSelection;

            if (DeviceInfo.Platform == DevicePlatform.iOS)
            {
                ToolbarItems.Add(_closeItem);
                _searchBar.Placeholder = AppResources.Search;
                _mainLayout.Children.Insert(0, _searchBar);
                _mainLayout.Children.Insert(1, _separator);
                ShowModalAnimationDelay = 0;
            }
            else
            {
                NavigationPage.SetTitleView(this, _titleLayout);
            }
            _autofillHandler = ServiceContainer.Resolve<IAutofillHandler>();
        }

        public SearchBar SearchBar => _searchBar;

        protected override async void OnAppearing()
        {
            base.OnAppearing();
            await _vm.InitAsync();
            if (!_hasFocused)
            {
                _hasFocused = true;
                RequestFocus(_searchBar);
            }
        }

        private void SearchBar_TextChanged(object sender, TextChangedEventArgs e)
        {
            var oldLength = e.OldTextValue?.Length ?? 0;
            var newLength = e.NewTextValue?.Length ?? 0;
            if (oldLength < 2 && newLength < 2 && oldLength < newLength)
            {
                return;
            }
            _vm.Search(e.NewTextValue, 200);
        }

        private void SearchBar_SearchButtonPressed(object sender, EventArgs e)
        {
            _vm.Search((sender as SearchBar).Text);
        }

        private void BackButton_Clicked(object sender, EventArgs e)
        {
            GoBack();
        }

        protected override bool OnBackButtonPressed()
        {
            if (string.IsNullOrWhiteSpace(_autofillUrl))
            {
                return false;
            }
            GoBack();
            return true;
        }

        private void GoBack()
        {
            if (!DoOnce())
            {
                return;
            }
            if (string.IsNullOrWhiteSpace(_autofillUrl))
            {
                Navigation.PopModalAsync(false);
            }
            else
            {
                _autofillHandler.CloseAutofill();
            }
        }

        private async void RowSelected(object sender, SelectionChangedEventArgs e)
        {
            ((ExtendedCollectionView)sender).SelectedItem = null;
            if (!DoOnce())
            {
                return;
            }

            if (e.CurrentSelection?.FirstOrDefault() is CipherItemViewModel cipherIteemVM)
            {
                await _vm.SelectCipherAsync(cipherIteemVM.Cipher);
            }
        }

        private void Close_Clicked(object sender, EventArgs e)
        {
            GoBack();
        }
    }
}
