package me.bbijabnpobatejb.multitogether.i18n;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Переводы. Файлы лежат в {@code plugins/MultiTogether/lang/<код>.yml}: при первом запуске туда
 * копируются встроенные, их можно править. Ключ, которого нет в файле, берётся из встроенного
 * перевода того же языка, а если нет и там — из английского.
 *
 * <p>Язык один на сервер: сообщения видят все, и цепь у всех одна.</p>
 */
@UtilityClass
public class Lang {

    public final String DEFAULT = "en";

    /**
     * Встроенные языки, в порядке показа в меню.
     */
    public final List<String> BUNDLED = List.of(
            "en", "ru", "uk", "de", "fr", "es", "pt", "it", "pl",
            "tr", "nl", "cs", "sv", "zh", "ja", "ko", "vi", "id"
    );

    private final MiniMessage MM = MiniMessage.miniMessage();

    private Plugin plugin;
    private File folder;
    private YamlConfiguration active = new YamlConfiguration();
    private String code = DEFAULT;
    private final Map<String, String> names = new LinkedHashMap<>();

    public void init(Plugin plugin) {
        Lang.plugin = plugin;
        folder = new File(plugin.getDataFolder(), "lang");

        for (String language : BUNDLED) {
            String resource = "lang/" + language + ".yml";
            if (!new File(folder, language + ".yml").exists() && plugin.getResource(resource) != null) {
                plugin.saveResource(resource, false);
            }
        }

        names.clear();
        for (String language : available()) {
            names.put(language, config(language).getString("language.name", language));
        }
    }

    /**
     * Встроенные языки и те, что админ положил в папку сам.
     */
    public List<String> available() {
        List<String> result = new ArrayList<>(BUNDLED);
        File[] files = folder == null ? null : folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return result;

        List<String> extra = new ArrayList<>();
        for (File file : files) {
            String language = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            if (!result.contains(language)) extra.add(language);
        }
        extra.sort(String::compareTo);
        result.addAll(extra);
        return result;
    }

    public boolean isAvailable(String language) {
        return language != null && available().contains(language.toLowerCase(Locale.ROOT));
    }

    public void load(String language) {
        String normalized = language.toLowerCase(Locale.ROOT);
        active = config(normalized);
        code = normalized;
    }

    /**
     * Файл с диска поверх встроенного перевода, встроенный — поверх английского.
     */
    private YamlConfiguration config(String language) {
        YamlConfiguration english = bundled(DEFAULT);
        YamlConfiguration base = language.equals(DEFAULT) ? english : bundled(language);
        if (base != english) base.setDefaults(english);

        File file = new File(folder, language + ".yml");
        if (!file.exists()) return base;

        YamlConfiguration disk = YamlConfiguration.loadConfiguration(file);
        disk.setDefaults(base);
        return disk;
    }

    private YamlConfiguration bundled(String language) {
        InputStream stream = plugin.getResource("lang/" + language + ".yml");
        if (stream == null) return new YamlConfiguration();

        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            plugin.getLogger().warning("Can't read bundled language " + language + ": " + e.getMessage());
            return new YamlConfiguration();
        }
    }

    public String code() {
        return code;
    }

    /**
     * Название языка на нём самом: «English», «Русский».
     */
    public String name(String language) {
        return names.getOrDefault(language, language);
    }

    /**
     * Текст без разметки: для описаний, которые потом переносятся по строкам, и сообщений о смерти.
     * Значения подставляются как есть.
     */
    public String plain(String key, Object... placeholders) {
        return fill(active.getString(key, key), placeholders);
    }

    /**
     * Текст для MiniMessage: сам перевод экранируется, а значения подставляются как разметка —
     * так в них можно покрасить ник, а переводчик ничего не сломает случайной скобкой.
     */
    public String mm(String key, Object... placeholders) {
        return fill(MM.escapeTags(active.getString(key, key)), placeholders);
    }

    private String fill(String template, Object... placeholders) {
        String result = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return result;
    }
}
