package com.prym.qa.dashboard;

import com.prym.qa.config.Config;
import com.prym.qa.db.TestResultRepository;
import com.prym.qa.db.TestResultRepository.Row;
import com.prym.qa.db.TestResultRepository.Summary;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A dashboard that reads execution history straight out of the results
 * database and renders it as a page: what ran, whether it passed, when, how
 * long it took, and a link to the run's report.
 *
 * <p>Built on the JDK's own HTTP server so the project needs no web framework.
 * Start it with:
 * {@code mvn exec:java -Dexec.mainClass=com.prym.qa.dashboard.DashboardServer}
 */
public final class DashboardServer {

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private DashboardServer() {
    }

    public static void main(String[] args) throws IOException {
        TestResultRepository.initSchema();
        int port = Config.dashboardPort();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", DashboardServer::handle);
        server.setExecutor(null);
        server.start();

        System.out.println("QA dashboard running at http://localhost:" + port);
        System.out.println("Press Ctrl+C to stop.");
    }

    private static void handle(HttpExchange exchange) throws IOException {
        byte[] body;
        String contentType = "text/html; charset=utf-8";
        try {
            body = render().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            contentType = "text/plain; charset=utf-8";
            body = ("Could not read the results database.\n\n" + e.getMessage()
                    + "\n\nRun the suite first:  mvn test")
                    .getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static String render() {
        Summary summary = TestResultRepository.summary();
        List<Row> rows = TestResultRepository.recentResults(100);
        int rate = summary.total() == 0 ? 0
                : Math.round(summary.passed() * 100f / summary.total());

        StringBuilder html = new StringBuilder(css());
        html.append("<div class=\"wrap\">")
                .append("<header>")
                .append("<p class=\"eyebrow\">Selenium &middot; Cucumber &middot; SQLite</p>")
                .append("<h1>Test execution dashboard</h1>")
                .append("<p class=\"sub\">Every scenario this suite has run, read live from the results database.</p>")
                .append("</header>");

        html.append("<section class=\"tiles\">")
                .append(tile("Executions", String.valueOf(summary.total()), "neutral"))
                .append(tile("Passed", String.valueOf(summary.passed()), "pass"))
                .append(tile("Failed", String.valueOf(summary.failed()), summary.failed() > 0 ? "fail" : "neutral"))
                .append(tile("Pass rate", rate + "%", rate == 100 ? "pass" : "warn"))
                .append(tile("Suite runs", String.valueOf(summary.runs()), "neutral"))
                .append("</section>");

        html.append("<section><h2>Execution history</h2><div class=\"tablewrap\"><table>")
                .append("<thead><tr>")
                .append("<th>#</th><th>Status</th><th>Scenario</th><th>Feature</th>")
                .append("<th>Tags</th><th>When</th><th>Duration</th><th>Report</th>")
                .append("</tr></thead><tbody>");

        if (rows.isEmpty()) {
            html.append("<tr><td colspan=\"8\" class=\"empty\">")
                    .append("No executions recorded yet. Run <code>mvn test</code> first.")
                    .append("</td></tr>");
        }
        for (Row r : rows) {
            boolean passed = "PASSED".equalsIgnoreCase(r.status());
            html.append("<tr>")
                    .append("<td class=\"num\">").append(r.id()).append("</td>")
                    .append("<td><span class=\"chip ").append(passed ? "pass" : "fail").append("\">")
                    .append(escape(r.status())).append("</span></td>")
                    .append("<td class=\"scenario\">").append(escape(r.scenario())).append("</td>")
                    .append("<td>").append(escape(r.feature())).append("</td>")
                    .append("<td class=\"tags\">").append(tags(r.tags())).append("</td>")
                    .append("<td class=\"num\">").append(when(r.executedAt())).append("</td>")
                    .append("<td class=\"num\">").append(seconds(r.durationMs())).append("</td>")
                    .append("<td>").append(reportLink(r.reportPath())).append("</td>")
                    .append("</tr>");
        }
        html.append("</tbody></table></div></section>")
                .append("<footer>Read directly from ").append(escape(Config.dbUrl()))
                .append(" &middot; refresh after a run to see new results</footer>")
                .append("</div>");
        return html.toString();
    }

    private static String tile(String label, String value, String tone) {
        return "<div class=\"tile " + tone + "\"><span class=\"tile-label\">" + label
                + "</span><span class=\"tile-value\">" + value + "</span></div>";
    }

    private static String tags(String raw) {
        if (raw == null || raw.isBlank()) {
            return "&mdash;";
        }
        StringBuilder sb = new StringBuilder();
        for (String t : raw.split(",")) {
            if (!t.isBlank()) {
                sb.append("<span class=\"tag\">").append(escape(t.trim())).append("</span>");
            }
        }
        return sb.toString();
    }

    private static String reportLink(String path) {
        if (path == null || path.isBlank()) {
            return "&mdash;";
        }
        return "<code>" + escape(path) + "</code>";
    }

    private static String when(String iso) {
        try {
            return WHEN.format(Instant.parse(iso));
        } catch (Exception e) {
            return escape(iso);
        }
    }

    private static String seconds(long ms) {
        return String.format("%.1fs", Duration.ofMillis(ms).toMillis() / 1000.0);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Tokenised light/dark palette, matching the suite's documentation styling. */
    private static String css() {
        return """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>QA Execution Dashboard</title><style>
                :root{--bg:#f6f7f7;--surface:#fff;--surface-2:#edf1f1;--ink:#12191c;--ink-2:#46545a;
                --ink-3:#6d7b81;--rule:#d7dedf;--accent:#0e6b74;--accent-soft:#dcecee;
                --pass:#2c7a4b;--pass-soft:#dcefe3;--fail:#b0402e;--fail-soft:#f7e2dd;
                --warn:#8a6512;--warn-soft:#f6ecd5;
                --mono:"Cascadia Code",Consolas,ui-monospace,monospace;
                --sans:"Segoe UI Variable Text","Segoe UI",system-ui,sans-serif;}
                @media (prefers-color-scheme:dark){:root:not([data-theme="light"]){
                --bg:#0e1416;--surface:#141d20;--surface-2:#1a2428;--ink:#e7eded;--ink-2:#a9b7ba;
                --ink-3:#7d8c90;--rule:#2a3639;--accent:#55c3cd;--accent-soft:#10353a;
                --pass:#64c48b;--pass-soft:#14301f;--fail:#e58b78;--fail-soft:#341a14;
                --warn:#d8ac53;--warn-soft:#322611;}}
                *{box-sizing:border-box}
                body{margin:0;background:var(--bg);color:var(--ink);font-family:var(--sans);
                font-size:15px;line-height:1.6}
                .wrap{max-width:1180px;margin:0 auto;padding:0 24px 72px}
                header{padding:48px 0 28px;border-bottom:1px solid var(--rule);margin-bottom:32px}
                .eyebrow{font-family:var(--mono);font-size:11px;letter-spacing:.16em;
                text-transform:uppercase;color:var(--accent);margin:0 0 10px}
                h1{margin:0;font-size:34px;letter-spacing:-.02em;line-height:1.1}
                .sub{margin:10px 0 0;color:var(--ink-2);max-width:60ch}
                h2{font-size:19px;margin:0 0 14px;letter-spacing:-.01em}
                .tiles{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));
                gap:12px;margin-bottom:40px}
                .tile{background:var(--surface);border:1px solid var(--rule);border-left:3px solid var(--rule);
                border-radius:5px;padding:14px 16px;display:flex;flex-direction:column;gap:4px}
                .tile.pass{border-left-color:var(--pass)}.tile.fail{border-left-color:var(--fail)}
                .tile.warn{border-left-color:var(--warn)}.tile.neutral{border-left-color:var(--accent)}
                .tile-label{font-family:var(--mono);font-size:10.5px;letter-spacing:.12em;
                text-transform:uppercase;color:var(--ink-3)}
                .tile-value{font-size:28px;font-weight:650;font-variant-numeric:tabular-nums}
                .tablewrap{overflow-x:auto;border:1px solid var(--rule);border-radius:5px;
                background:var(--surface)}
                table{border-collapse:collapse;width:100%;font-size:14px}
                th,td{text-align:left;padding:10px 13px;border-bottom:1px solid var(--rule);
                vertical-align:top}
                thead th{font-family:var(--mono);font-size:10.5px;letter-spacing:.11em;
                text-transform:uppercase;color:var(--ink-3);background:var(--surface-2);
                white-space:nowrap}
                tbody tr:last-child td{border-bottom:0}
                td.num{font-family:var(--mono);font-variant-numeric:tabular-nums;
                white-space:nowrap;font-size:12.5px;color:var(--ink-2)}
                td.scenario{font-weight:550;min-width:260px}
                .chip{font-family:var(--mono);font-size:10.5px;font-weight:600;letter-spacing:.06em;
                padding:2px 7px;border-radius:3px;white-space:nowrap}
                .chip.pass{background:var(--pass-soft);color:var(--pass)}
                .chip.fail{background:var(--fail-soft);color:var(--fail)}
                .tag{display:inline-block;font-family:var(--mono);font-size:10.5px;
                background:var(--accent-soft);color:var(--accent);padding:1px 6px;
                border-radius:3px;margin:0 4px 3px 0}
                td.empty{text-align:center;color:var(--ink-3);padding:32px}
                code{font-family:var(--mono);font-size:11.5px;color:var(--ink-2)}
                footer{margin-top:28px;padding-top:16px;border-top:1px solid var(--rule);
                font-family:var(--mono);font-size:11px;color:var(--ink-3)}
                </style></head><body>
                """;
    }
}
