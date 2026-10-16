import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";

/**
 * Display labels for topics (sequence tags) in the UI's language.
 *
 * A topic's canonical name is a German slug; the semantic tagger also stores an English slug
 * for it. When the UI runs in English, this resolves a canonical name to that English slug,
 * falling back to the name itself (manual topics, or ones tagged before topics were bilingual).
 * Filters, URLs and API calls keep using the canonical name — only rendering goes through here.
 *
 * The translation map is fetched once and shared by every caller. A failed fetch is swallowed:
 * the canonical names are still perfectly readable, just German.
 */

type Translations = Record<string, string>;

let cache: Translations | null = null;
let pending: Promise<Translations> | null = null;
const listeners = new Set<(t: Translations) => void>();

function load(): Promise<Translations> {
    if (!pending) {
        pending = api
            .get<Translations>("/library/tags/translations")
            .then((res) => {
                cache = res.data ?? {};
                listeners.forEach((l) => l(cache!));
                return cache;
            })
            .catch(() => {
                pending = null; // let a later caller retry
                return cache ?? {};
            });
    }
    return pending;
}

/**
 * Refetches the translation map, e.g. once freshly generated LLM topics have landed, so their
 * English labels show up without a reload.
 */
export function refreshTopicLabels(): void {
    pending = null;
    void load();
}

/** Returns a function mapping a canonical topic name to its label in the current UI language. */
export function useTopicLabel(): (tagName: string) => string {
    const { i18n } = useTranslation();
    const english = (i18n.language ?? "").startsWith("en");
    const [translations, setTranslations] = useState<Translations | null>(cache);

    useEffect(() => {
        if (!english) return;
        listeners.add(setTranslations);
        if (cache) setTranslations(cache);
        else void load();
        return () => {
            listeners.delete(setTranslations);
        };
    }, [english]);

    return useCallback(
        (tagName: string) => (english && translations?.[tagName]) || tagName,
        [english, translations],
    );
}
