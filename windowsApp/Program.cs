using System;
using System.Drawing;
using System.IO;
using System.Windows.Forms;

namespace MesaiTakipWindows
{
    public class MainForm : Form
    {
        public MainForm()
        {
            this.Text = "Mesai Takip v31.0 — Mesai, Maaş ve Bütçe Yönetimi";
            this.Width = 1120;
            this.Height = 760;
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = Color.FromArgb(248, 250, 252);

            WebBrowser browser = new WebBrowser();
            browser.Dock = DockStyle.Fill;
            browser.ScriptErrorsSuppressed = true;

            string localHtml = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "index.html");
            if (File.Exists(localHtml))
            {
                browser.Url = new Uri(localHtml);
            }
            else
            {
                browser.DocumentText = "<html><body style='font-family:Segoe UI,sans-serif;padding:32px;'><h1>Mesai Takip v31.0</h1><p>Uygulama hazir.</p></body></html>";
            }

            this.Controls.Add(browser);
        }

        [STAThread]
        public static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }
}
