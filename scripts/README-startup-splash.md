# Startup artwork

Generated from the bundled logo and KittyTune Flex font. Regenerate from the repository root with the packaging JDK:

```text
java -Djava.awt.headless=true scripts/GenerateStartupSplash.java src/main/resources/icons/kittytune.png packaging-resources/common/startup.gif preview.png production src/main/resources/fonts/kittytune_flex.ttf src/main/resources/startup/text.png
```

The production GIF animates only the waves and starts at 0%. Runtime text/progress use actual startup milestones. The optional `demo` mode advances representative stages for design previews only; never package the demo GIF.

The text atlas contains eight stages for each language in this order: en, de, fr, hu, pt-BR, ru, tr, vi. Each row is 312×26 pixels. Rebuild it whenever startup labels or languages change. It avoids parsing the variable font during startup.
