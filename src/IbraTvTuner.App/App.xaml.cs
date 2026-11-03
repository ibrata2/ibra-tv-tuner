using System.Windows;
using System.Windows.Threading;
using IbraTvTuner.Core.Storage;

namespace IbraTvTuner.App;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        Loc.I.Lang = AppState.I.Settings.Language;
        DispatcherUnhandledException += OnUnhandled;
        base.OnStartup(e);
    }

    private void OnUnhandled(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        Log.Write("UNHANDLED " + e.Exception);
        Ui.Error(e.Exception);
        e.Handled = true;
    }
}
