using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using IbraTvTuner.Core.Platforms;

namespace IbraTvTuner.App.Pages;

/// <summary>Draws the compatibility matrix straight from PlatformRegistry, so the UI can never claim more than the code.</summary>
public partial class CompatibilityPage : UserControl, IPage
{
    public CompatibilityPage()
    {
        InitializeComponent();
        Build();
    }

    public void OnShown() { }
    public void OnLanguageChanged() => Build();

    private static string FeatureName(Feature f) => f switch
    {
        Feature.ConnectNetwork => Loc.Pick("اتصال ADB عبر الشبكة", "ADB over network"),
        Feature.WirelessDebuggingPairing => Loc.Pick("اقتران Wireless debugging", "Wireless debugging pairing"),
        Feature.ConnectUsb => Loc.Pick("اتصال USB", "USB connection"),
        Feature.LanDiscovery => Loc.Pick("اكتشاف في الشبكة", "LAN discovery"),
        Feature.ReadDeviceInfo => Loc.Pick("قراءة الموديل والنظام", "Read model/system"),
        Feature.InventoryApps => Loc.Pick("جرد التطبيقات", "App inventory"),
        Feature.DisableApp => Loc.Pick("تعطيل/تفعيل تطبيق", "Disable/enable app"),
        Feature.UninstallForUser => Loc.Pick("إزالة للمستخدم وإرجاع", "Remove for user + restore"),
        Feature.InstallLauncher => Loc.Pick("تثبيت واجهة IBRA", "Install IBRA launcher"),
        Feature.SetLauncherAsHome => Loc.Pick("تعيينها رئيسية", "Set as Home"),
        Feature.PushLauncherTheme => Loc.Pick("إرسال الشكل", "Send look"),
        Feature.RestoreOriginalHome => Loc.Pick("رجوع للشكل القديم", "Restore old look"),
        Feature.LaunchTiming => Loc.Pick("قياس وقت الفتح", "Launch timing"),
        _ => f.ToString(),
    };

    private Brush BrushFor(FeatureStatus s) => (Brush)FindResource(s switch
    {
        FeatureStatus.Supported => "Ok",
        FeatureStatus.NeedsSetup => "Accent",
        FeatureStatus.Untested => "Warn",
        _ => "Danger",
    });

    private void Build()
    {
        Matrix.Children.Clear();
        Matrix.RowDefinitions.Clear();
        Matrix.ColumnDefinitions.Clear();
        var platforms = PlatformRegistry.All;
        var features = Enum.GetValues<Feature>();
        Matrix.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(220) });
        foreach (var _ in platforms) Matrix.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(130) });
        for (int i = 0; i <= features.Length; i++) Matrix.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        void Cell(int r, int c, UIElement e) { Grid.SetRow(e, r); Grid.SetColumn(e, c); Matrix.Children.Add(e); }

        Cell(0, 0, new TextBlock { Text = Loc.T("Feature"), Foreground = (Brush)FindResource("Muted"), Margin = new Thickness(4, 4, 4, 10) });
        for (int c = 0; c < platforms.Count; c++)
            Cell(0, c + 1, new TextBlock { Text = platforms[c].DisplayName, FontWeight = FontWeights.SemiBold, Margin = new Thickness(4, 4, 4, 10) });

        for (int r = 0; r < features.Length; r++)
        {
            var f = features[r];
            Cell(r + 1, 0, new TextBlock { Text = FeatureName(f), Margin = new Thickness(4, 6, 4, 6) });
            for (int c = 0; c < platforms.Count; c++)
            {
                var entry = platforms[c].Features.FirstOrDefault(x => x.Feature == f);
                var st = entry?.Status ?? FeatureStatus.Unsupported;
                var note = entry is null ? "" : (Loc.I.IsArabic ? entry.NoteAr : entry.NoteEn);
                var tb = new Border
                {
                    Margin = new Thickness(3), Padding = new Thickness(6, 3, 6, 3), CornerRadius = new CornerRadius(6),
                    BorderBrush = BrushFor(st), BorderThickness = new Thickness(1),
                    Child = new TextBlock { Text = AppState.StatusName(st), Foreground = BrushFor(st), FontSize = 12.5 },
                    ToolTip = string.IsNullOrEmpty(note) ? null : note,
                };
                Cell(r + 1, c + 1, tb);
            }
        }

        Notes.Children.Clear();
        Notes.Children.Add(new TextBlock { Text = Loc.Pick("ملاحظات المنصات", "Platform notes"), Style = (Style)FindResource("H2"), Margin = new Thickness(0, 0, 0, 6) });
        foreach (var p in platforms.Where(p => !p.UsesAdb))
        {
            var n = p.Features.First();
            Notes.Children.Add(new TextBlock { Text = $"• {p.DisplayName}: {(Loc.I.IsArabic ? n.NoteAr : n.NoteEn)}", Margin = new Thickness(0, 2, 0, 4) });
        }
        foreach (var (name, ar, en) in PlatformRegistry.ReferenceOnly)
            Notes.Children.Add(new TextBlock { Text = $"• {name}: {(Loc.I.IsArabic ? ar : en)}", Margin = new Thickness(0, 2, 0, 4) });
        Notes.Children.Add(new TextBlock
        {
            Style = (Style)FindResource("Hint"),
            Text = Loc.Pick("مرّر الماوس على أي خانة لقراءة الملاحظة. التفاصيل وما اختُبر فعلياً في docs/TESTING.md بالريبو.",
                            "Hover any cell for its note. What has actually been tested is in docs/TESTING.md in the repo."),
        });
    }
}
