import sys, time
from playwright.sync_api import sync_playwright

OUT = "/home/user/Doubao/chats/38448800358997506/word-dungeon/docs/agent-flow/screenshots"
BASE = "http://127.0.0.1:8000/"
done, failed = [], []

def shot(page, name):
    p = f"{OUT}/{name}"
    page.screenshot(path=p)
    done.append(name)
    print("OK", name)

with sync_playwright() as pw:
    b = pw.chromium.launch(executable_path="/usr/local/bin/chromium", args=["--no-sandbox"])
    ctx = b.new_context(viewport={"width":390,"height":844}, device_scale_factor=2,
                        is_mobile=True, has_touch=True)
    page = ctx.new_page()
    page.goto(BASE, wait_until="networkidle")
    page.wait_for_timeout(600)

    # 1 home (campaign default)
    shot(page, "01-home.png")

    # 2 settings (dock gear)
    try:
        page.click('button[data-act="settings"]', timeout=3000); page.wait_for_timeout(400)
        shot(page, "02-settings.png")
        page.click('button.back[data-act="homeback"]', timeout=3000); page.wait_for_timeout(300)
    except Exception as e: failed.append(f"02-settings:{e}")

    # 3 wrongbook
    try:
        page.click('button[data-act="wrongbook"]', timeout=3000); page.wait_for_timeout(400)
        shot(page, "03-wrongbook.png")
        page.click('button[data-act="wrongback"]', timeout=3000); page.wait_for_timeout(300)
    except Exception as e: failed.append(f"03-wrongbook:{e}")

    # 4 achievements
    try:
        page.click('button[data-act="achievements"]', timeout=3000); page.wait_for_timeout(400)
        shot(page, "04-achievements.png")
        page.click('button[data-act="homeback"]', timeout=3000); page.wait_for_timeout(300)
    except Exception as e: failed.append(f"04-achievements:{e}")

    # 5 quick mode home (quick tab + 3 levels)
    try:
        page.click('.mode-tab.quick', timeout=3000); page.wait_for_timeout(400)
        shot(page, "05-home-quick.png")
    except Exception as e: failed.append(f"05-home-quick:{e}")

    # 6 quick battle
    try:
        page.click('.launch-btn[data-act="launch"]', timeout=3000); page.wait_for_timeout(700)
        shot(page, "06-battle-quick.png")
        page.click('span.quick-exit[data-act="quickexit"]', timeout=3000); page.wait_for_timeout(400)
    except Exception as e: failed.append(f"06-battle-quick:{e}")

    # 7 campaign battle (enemy card + ladder + timer)
    try:
        page.click('.mode-tab.campaign', timeout=3000); page.wait_for_timeout(300)
        page.click('.launch-btn[data-act="launch"]', timeout=3000); page.wait_for_timeout(800)
        shot(page, "07-battle-campaign.png")
    except Exception as e: failed.append(f"07-battle-campaign:{e}")

    # 8 choice / flip-card (inject state)
    try:
        page.evaluate("""(()=>{
            G.choice={slots:[{card:CMAP['shield'],rare:0},{card:CMAP['flash'],rare:1},{card:CMAP['deadly'],rare:2}],
                      reward:15, kind:'normal', nodeKind:'normal', pityBefore:{p:0,e:0}};
            G.battle=null; G.screen='choice'; render();
        })()""")
        page.wait_for_timeout(1800)  # let autoReveal play out
        shot(page, "08-choice-flip.png")
    except Exception as e: failed.append(f"08-choice:{e}")

    # 9 result (inject)
    try:
        page.evaluate("""(()=>{
            G.result='win'; G.mode='campaign'; G.screen='result';
            G.stats={questions:20,correct:17,wrong:3,maxCombo:9,newWords:new Set(['abandon','benefit','concept']),reviewWords:new Set(),kills:3};
            G.wrongWords={'benifit':true};
            render();
        })()""")
        page.wait_for_timeout(500)
        shot(page, "09-result.png")
    except Exception as e: failed.append(f"09-result:{e}")

    # 10 spring (复习之泉)
    try:
        page.evaluate("""(()=>{ G.wrongWords={'abandon':true,'benefit':true}; G.wrongCount={'abandon':3,'benefit':1}; startSpring(); })()""")
        page.wait_for_timeout(500)
        shot(page, "10-spring.png")
    except Exception as e: failed.append(f"10-spring:{e}")

    # 11 shop (词境商店)
    try:
        page.evaluate("""(()=>{ G.gold=120; startShop(); })()""")
        page.wait_for_timeout(500)
        shot(page, "11-shop.png")
    except Exception as e: failed.append(f"11-shop:{e}")

    # 12 back-confirm dialog (battle -> home confirm). Trigger by injecting and showing modal.
    try:
        page.evaluate("""(()=>{ newRun('campaign'); G.screen='home'; render(); })()""")
        page.wait_for_timeout(300)
    except Exception as e: failed.append(f"12-cleanup:{e}")

    b.close()

print("DONE_OK:", done)
print("DONE_FAIL:", failed)
