using System.Windows;
using IbraTvTuner.Core.Changes;
using IbraTvTuner.Core.Packages;

namespace IbraTvTuner.App;

public sealed record PlanRow(string Package, string Before, string Change, string Reason);

/// <summary>Shows exactly what will change, on which device and why, before anything runs.</summary>
public partial class PlanWindow : Window
{
    public PlanWindow(ChangeBatch batch, List<Core.Changes.PlanIssue> issues)
    {
        InitializeComponent();
        TargetText.Text = Loc.T("Target") + " " + AppState.I.TargetName;
        CPkg.Header = Loc.T("ColPackage"); CBefore.Header = Loc.T("Before"); CChange.Header = Loc.T("Change"); CReason.Header = Loc.T("ColReason");
        ItemsGrid.ItemsSource = batch.Items.Select(i => new PlanRow(i.Package, StateText(i.Before), ActionText(i.Action), i.Reason)).ToList();
        IssuesList.ItemsSource = issues.Select(i => $"{Loc.T("Skipped")}: {i.Package} — {(Loc.I.IsArabic ? i.Ar : i.En)}").ToList();
        NoteText.Text = Loc.Pick("الحالة الحالية تُحفظ قبل التنفيذ، وبعد كل خطوة يُعاد الاستعلام من الجهاز للتحقق. الاسترجاع من صفحة «التغييرات والاسترجاع».",
                                 "Current state is saved first, and every step is re-read from the device to verify it. Roll back from 'Changes & restore'.");
        ApplyBtn.IsEnabled = batch.Items.Count > 0;
    }

    internal static string StateText(PackageState s) => s switch
    {
        PackageState.Disabled => Loc.Pick("معطّل", "disabled"),
        PackageState.UninstalledForUser => Loc.Pick("مُزال", "removed"),
        _ => Loc.Pick("مفعّل", "enabled"),
    };

    internal static string ActionText(ChangeAction a) => a switch
    {
        ChangeAction.Disable => Loc.Pick("تعطيل", "Disable"),
        ChangeAction.Enable => Loc.Pick("تفعيل", "Enable"),
        ChangeAction.UninstallForUser => Loc.Pick("إزالة للمستخدم", "Remove for user"),
        _ => Loc.Pick("إرجاع للمستخدم", "Restore for user"),
    };

    private void Apply_Click(object sender, RoutedEventArgs e) => DialogResult = true;
}
