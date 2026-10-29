package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.service.DashboardService;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/{recurso:resumo|vendas|status|produtos}")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable String recurso,
            @RequestParam(required = false) String dataInicial, @RequestParam(required = false) String dataFinal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dashboard.consultar(dataInicial, dataFinal, recurso));
    }
}
