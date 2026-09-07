package com.sablednah.standards.client;

/**
 * Strings on the client, which is a smaller problem than it looks.
 *
 * <p>Standards resolves text <b>server-side</b> — {@code neoforge/Lang}, written to
 * {@code messages.yml} — precisely because a vanilla client carries no lang file of ours and would
 * see raw keys. The client half cannot reach that, and must not start a second catalogue that would
 * drift from it.</p>
 *
 * <p>So this does the least it can: it turns a key into something readable when nothing better is
 * available. The tooltips it feeds are conveniences on a bar that is itself a convenience — and the
 * authoritative text still arrives the moment the action runs and the server answers in chat, in
 * the owner's own words.</p>
 *
 * <p>The better fix, if tooltips ever matter enough: send the resolved strings alongside the
 * capability set, since the server already knows them. Deliberately not done yet — it is payload
 * for a cosmetic gain, and this seam's whole argument is that the client adds nothing load-bearing.</p>
 */
public final class ClientLang {

    /** {@code "msg.toggle.vanish"} → {@code "Vanish"}. */
    public static String get(String key) {
        int dot = key.lastIndexOf('.');
        String tail = dot < 0 ? key : key.substring(dot + 1);
        String spaced = tail.replace('_', ' ');
        return spaced.isEmpty() ? key
                : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private ClientLang() {}
}
