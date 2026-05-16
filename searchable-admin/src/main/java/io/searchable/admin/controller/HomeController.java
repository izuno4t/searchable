package io.searchable.admin.controller;

import io.searchable.core.application.IndexStatisticsService;
import io.searchable.core.application.IndexStatisticsService.Statistics;
import io.searchable.core.application.NamespaceService;
import io.searchable.core.application.SearchPerformanceMonitor;
import io.searchable.core.application.SearchPerformanceMonitor.Sample;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard page rendering aggregate statistics and recent search latency.
 */
@Controller
public class HomeController {

    private static final DateTimeFormatter HOUR_MIN = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneOffset.UTC);

    private final NamespaceService namespaces;
    private final IndexStatisticsService statisticsService;
    private final SearchPerformanceMonitor monitor;

    public HomeController(final NamespaceService namespaces,
                          final IndexStatisticsService statisticsService,
                          final SearchPerformanceMonitor monitor) {
        this.namespaces = namespaces;
        this.statisticsService = statisticsService;
        this.monitor = monitor;
    }

    @GetMapping("/")
    public String index(final Model model) {
        final Statistics stats = statisticsService.aggregate();
        final SearchPerformanceMonitor.Summary perf = monitor.summary();

        final List<String> chartLabels = new ArrayList<>();
        final List<Long> chartData = new ArrayList<>();
        for (final Sample sample : perf.samples()) {
            chartLabels.add(HOUR_MIN.format(sample.timestamp()));
            chartData.add(sample.latencyMs());
        }

        model.addAttribute("activeNav", "dashboard");
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("stats", stats);
        model.addAttribute("perf", perf);
        model.addAttribute("namespaces", namespaces.listAll());
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartData", chartData);
        return "home";
    }
}
