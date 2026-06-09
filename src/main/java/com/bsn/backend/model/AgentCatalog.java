package com.bsn.backend.model;

import java.util.List;
import java.util.Optional;

/**
 * Static catalog of marketplace AI agents. Prices are in INR (rupees).
 * Kept in sync with the front-end agent grid (Dashboard.jsx).
 */
public final class AgentCatalog {

    public record CatalogAgent(
            String id,
            String icon,
            String name,
            String tag,
            String description,
            List<String> features,
            int priceInr,
            boolean purchasable
    ) {}

    public static final List<CatalogAgent> AGENTS = List.of(
            new CatalogAgent(
                    "investment", "📊", "Investment Analyzer", "LIVE",
                    "AI-powered analysis of investment memos, pitch decks & financials. Confidence scores, risk flags, and follow-up questions instantly.",
                    List.of("Confidence scoring", "Risk & red-flag detection", "Follow-up questions"),
                    4999, true
            ),
            new CatalogAgent(
                    "3d-world", "🌐", "3D World Architect", "BETA",
                    "Design, simulate and deploy immersive 3D business environments. Build photorealistic digital twins of your operations.",
                    List.of("Virtual environment builder", "Real-time physics sim", "Export to Unity / Unreal"),
                    14999, true
            ),
            new CatalogAgent(
                    "ops", "🤖", "Autonomous Ops Agent", "LIVE",
                    "Deploy intelligent agents that automate operations end-to-end — procurement, scheduling, reporting — slashing costs by 70%+.",
                    List.of("Workflow automation", "Cost reduction analytics", "24/7 agent monitoring"),
                    9999, true
            ),
            new CatalogAgent(
                    "digital-twin", "♾️", "Digital Twin Engine", "BETA",
                    "Create real-time AI digital twins for any industry — manufacturing, healthcare, logistics — and run predictive simulations.",
                    List.of("Real-time sensor sync", "Predictive failure modeling", "Industry templates"),
                    12999, true
            ),
            new CatalogAgent(
                    "ocean", "🌊", "Ocean Revival Agent", "RESEARCH",
                    "Monitor marine ecosystems, coordinate AI-guided ocean cleanup fleets, and track real-time health metrics globally.",
                    List.of("Satellite data ingestion", "Cleanup fleet coordination", "Ecosystem health reports"),
                    0, false
            ),
            new CatalogAgent(
                    "multiverse", "🌌", "Multiverse Explorer", "SOON",
                    "Theoretical AI research into consciousness across dimensions. Access multiverse simulations and emotion-driven energy mappings.",
                    List.of("Consciousness mapping AI", "Dimension-shift modeling", "Research paper access"),
                    0, false
            )
    );

    public static Optional<CatalogAgent> findById(String id) {
        return AGENTS.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    private AgentCatalog() {}
}
