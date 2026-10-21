// SPDX-License-Identifier: GPL-3.0-or-later
package net.slooopipe.auth.api;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Exact Google account hosts from a pinned domain list; no runtime network lookup. */
final class GoogleAccountDomains {
    // Source: https://www.google.com/supported_domains, retrieved 2026-10-20.
    private static final Set<String> DOMAINS =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    ("google.ad google.ae google.al google.am google.as google.at"
                                         + " google.az google.ba google.be google.bf google.bg"
                                         + " google.bi google.bj google.bs google.bt google.by"
                                         + " google.ca google.cat google.cd google.cf google.cg"
                                         + " google.ch google.ci google.cl google.cm google.cn"
                                         + " google.co.ao google.co.bw google.co.ck google.co.cr"
                                         + " google.co.id google.co.il google.co.in google.co.jp"
                                         + " google.co.ke google.co.kr google.co.ls google.co.ma"
                                         + " google.co.mz google.co.nz google.co.th google.co.tz"
                                         + " google.co.ug google.co.uk google.co.uz google.co.ve"
                                         + " google.co.vi google.co.za google.co.zm google.co.zw"
                                         + " google.com google.com.af google.com.ag google.com.ar"
                                         + " google.com.au google.com.bd google.com.bh"
                                         + " google.com.bn google.com.bo google.com.br"
                                         + " google.com.bz google.com.co google.com.cu"
                                         + " google.com.cy google.com.do google.com.ec"
                                         + " google.com.eg google.com.et google.com.fj"
                                         + " google.com.gh google.com.gi google.com.gt"
                                         + " google.com.hk google.com.jm google.com.kh"
                                         + " google.com.kw google.com.lb google.com.ly"
                                         + " google.com.mm google.com.mt google.com.mx"
                                         + " google.com.my google.com.na google.com.ng"
                                         + " google.com.ni google.com.np google.com.om"
                                         + " google.com.pa google.com.pe google.com.pg"
                                         + " google.com.ph google.com.pk google.com.pr"
                                         + " google.com.py google.com.qa google.com.sa"
                                         + " google.com.sb google.com.sg google.com.sl"
                                         + " google.com.sv google.com.tj google.com.tr"
                                         + " google.com.tw google.com.ua google.com.uy"
                                         + " google.com.vc google.com.vn google.cv google.cz"
                                         + " google.de google.dj google.dk google.dm google.dz"
                                         + " google.ee google.es google.fi google.fm google.fr"
                                         + " google.ga google.ge google.gg google.gl google.gm"
                                         + " google.gr google.gy google.hn google.hr google.ht"
                                         + " google.hu google.ie google.im google.iq google.is"
                                         + " google.it google.je google.jo google.kg google.ki"
                                         + " google.kz google.la google.li google.lk google.lt"
                                         + " google.lu google.lv google.md google.me google.mg"
                                         + " google.mk google.ml google.mn google.mu google.mv"
                                         + " google.mw google.ne google.nl google.no google.nr"
                                         + " google.nu google.pl google.pn google.ps google.pt"
                                         + " google.ro google.rs google.ru google.rw google.sc"
                                         + " google.se google.sh google.si google.sk google.sm"
                                         + " google.sn google.so google.sr google.st google.td"
                                         + " google.tg google.tl google.tm google.tn google.to"
                                         + " google.tt google.vu google.ws")
                                            .split(" "))));

    private GoogleAccountDomains() { }

    static boolean containsAccountHost(final String host) {
        return host != null
                && host.startsWith("accounts.")
                && DOMAINS.contains(host.substring("accounts.".length()));
    }
}
