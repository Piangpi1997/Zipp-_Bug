'use strict';

(function () {
  const isBurmese = new URLSearchParams(window.location.search).get('lang') === 'my';
  const translations = {
    'LIKEFIGMA Studio': 'LIKEFIGMA စတူဒီယို',
    'Loading project…': 'ပရောဂျက် ဖွင့်နေသည်…',
    '↩ Undo': '↩ နောက်ပြန်',
    '↪ Redo': '↪ ပြန်လုပ်',
    'Export XML': 'XML ထုတ်ယူရန်',
    'Export Compose': 'Compose ထုတ်ယူရန်',
    'Save': 'သိမ်းရန်',
    'Screen:': 'မျက်နှာပြင်:',
    '+ Screen': '+ မျက်နှာပြင်',
    'Rename': 'အမည်ပြောင်းရန်',
    'Duplicate': 'မိတ္တူပွားရန်',
    'Delete': 'ဖျက်ရန်',
    'Editor tools': 'တည်းဖြတ်ကိရိယာများ',
    '+ Text': '+ စာသား',
    '+ Button': '+ ခလုတ်',
    '+ Input': '+ ထည့်သွင်းရန်',
    '+ Card': '+ ကတ်',
    '+ Image': '+ ပုံ',
    'Snap 8px': '8px ဂရစ်ဖြင့်ညှိရန်',
    'Align left': 'ဘယ်ဘက်ညှိရန်',
    'Align left edges': 'ဘယ်ဘက်အစွန်းများ ညှိရန်',
    'Align center X': 'X ဗဟိုညှိရန်',
    'Align horizontal centers': 'အလျားလိုက် ဗဟိုများ ညှိရန်',
    'Align top': 'အပေါ်ဘက်ညှိရန်',
    'Align top edges': 'အပေါ်အစွန်းများ ညှိရန်',
    'Align center Y': 'Y ဗဟိုညှိရန်',
    'Align vertical centers': 'ဒေါင်လိုက် ဗဟိုများ ညှိရန်',
    'Distribute H': 'အလျားလိုက် ညီမျှခွဲရန်',
    'Distribute horizontally': 'အလျားလိုက် ညီမျှခွဲရန်',
    'Distribute V': 'ဒေါင်လိုက် ညီမျှခွဲရန်',
    'Distribute vertically': 'ဒေါင်လိုက် ညီမျှခွဲရန်',
    'Design canvas': 'ဒီဇိုင်းကင်းဗတ်',
    'Phone screen preview': 'ဖုန်းမျက်နှာပြင် အစမ်းမြင်ကွင်း',
    'Layers': 'အလွှာများ',
    'Bring up': 'အရှေ့သို့ယူရန်',
    'Send down': 'နောက်သို့ပို့ရန်',
    'Hide': 'ဖျောက်ရန်',
    'Show': 'ပြရန်',
    'Inspector': 'စစ်ဆေးတည်းဖြတ်ရန်',
    'Text / Content': 'စာသား / အကြောင်းအရာ',
    'Select a layer': 'အလွှာတစ်ခု ရွေးချယ်ပါ',
    'Font size': 'ဖောင့်အရွယ်အစား',
    'Radius': 'ထောင့်ဝိုင်းမှု',
    'Fill': 'ဖြည့်အရောင်',
    'Text color': 'စာသားအရောင်',
    'Import / recovery': 'ထည့်သွင်းခြင်း / ပြန်လည်ရယူခြင်း',
    'Import project JSON (supports legacy projects)': 'JSON ပရောဂျက် ထည့်သွင်းရန် (ဟောင်းသောပရောဂျက်များကိုလည်း လက်ခံသည်)',
    'Restore last good save': 'နောက်ဆုံးအောင်မြင်သိမ်းဆည်းမှုကို ပြန်ယူရန်',
    'Project schema': 'ပရောဂျက် ဖွဲ့စည်းပုံ',
    'Project JSON preview': 'ပရောဂျက် JSON အစမ်းမြင်ကွင်း',
    'New screen name:': 'မျက်နှာပြင်အသစ် အမည်:',
    'Rename screen:': 'မျက်နှာပြင် အမည်ပြောင်းရန်:',
    'Delete the current screen and its layers?': 'လက်ရှိမျက်နှာပြင်နှင့် ၎င်း၏အလွှာများကို ဖျက်မလား။',
    'Cannot delete the only screen in the project.': 'ပရောဂျက်ရှိ တစ်ခုတည်းသော မျက်နှာပြင်ကို ဖျက်၍မရပါ။',
    'Select at least two layers (Shift-click or Ctrl/Cmd-click) to align.': 'ညှိရန် အလွှာအနည်းဆုံး ၂ ခုကို ရွေးပါ (Shift-click သို့မဟုတ် Ctrl/Cmd-click).',
    'Select at least three layers to distribute them evenly.': 'ညီမျှစွာ ခွဲရန် အလွှာအနည်းဆုံး ၃ ခုကို ရွေးပါ။',
    'Unsaved changes…': 'မသိမ်းဆည်းရသေးသော ပြောင်းလဲမှုများ…',
    'Saving…': 'သိမ်းဆည်းနေသည်…',
    'New project · autosave enabled': 'ပရောဂျက်အသစ် · အလိုအလျောက်သိမ်းဆည်းမှု ဖွင့်ထားသည်',
    'No valid saved project was found. The last good save remains available for recovery.': 'မှန်ကန်သော သိမ်းဆည်းထားသည့် ပရောဂျက် မတွေ့ပါ။ နောက်ဆုံးအောင်မြင်သိမ်းဆည်းမှုကို ပြန်လည်ရယူနိုင်သေးသည်။',
    'No last-good browser save is available.': 'ဘရောက်ဇာတွင် နောက်ဆုံးအောင်မြင်သိမ်းဆည်းမှု မရှိပါ။',
    'XML export ready. Device export is unavailable in this preview.': 'XML ထုတ်ယူရန် အသင့်ဖြစ်ပါပြီ။ ဤအစမ်းမြင်ကွင်းတွင် စက်သို့ တိုက်ရိုက်ထုတ်ယူ၍ မရပါ။',
    'Compose export ready. Device export is unavailable in this preview.': 'Compose ထုတ်ယူရန် အသင့်ဖြစ်ပါပြီ။ ဤအစမ်းမြင်ကွင်းတွင် စက်သို့ တိုက်ရိုက်ထုတ်ယူ၍ မရပါ။'
  };

  const prefixes = [
    ['Unsupported layer type: ', 'မထောက်ပံ့သော အလွှာအမျိုးအစား: '],
    ['Not saved: ', 'မသိမ်းဆည်းနိုင်ပါ: '],
    ['Autosave failed: ', 'အလိုအလျောက်သိမ်းဆည်းမှု မအောင်မြင်ပါ: '],
    ['Local autosave saved; Android save failed: ', 'စက်တွင်း အလိုအလျောက်သိမ်းဆည်းပြီး၊ Android တွင် သိမ်းဆည်းမှု မအောင်မြင်ပါ: '],
    ['Saved on device · ', 'စက်တွင် သိမ်းဆည်းပြီး · '],
    ['Autosaved in this WebView · ', 'ဤ WebView တွင် အလိုအလျောက်သိမ်းဆည်းပြီး · '],
    ['Local autosave saved; device save failed: ', 'စက်တွင်း အလိုအလျောက်သိမ်းဆည်းပြီး၊ စက်တွင် သိမ်းဆည်းမှု မအောင်မြင်ပါ: '],
    ['Recovered from ', 'ပြန်လည်ရယူခဲ့သည့်နေရာ: '],
    ['Restored the last good save.', 'နောက်ဆုံးအောင်မြင်သိမ်းဆည်းမှုကို ပြန်ယူပြီးပါပြီ။'],
    ['The backup could not be restored: ', 'အရန်သိမ်းဆည်းမှုကို ပြန်မယူနိုင်ပါ: '],
    ['Import failed: ', 'ထည့်သွင်းမှု မအောင်မြင်ပါ: '],
    ['Imported project successfully.', 'ပရောဂျက်ကို အောင်မြင်စွာ ထည့်သွင်းပြီးပါပြီ။'],
    ['Android XML exported for ', 'Android XML ထုတ်ယူပြီး: '],
    ['Jetpack Compose source exported for ', 'Jetpack Compose source ထုတ်ယူပြီး: ']
  ];

  function translate(value) {
    if (!isBurmese || typeof value !== 'string') return value;
    if (translations[value]) return translations[value];
    for (const [english, burmese] of prefixes) {
      if (value.startsWith(english)) return burmese + value.slice(english.length);
    }
    return value;
  }

  window.zipBugTranslate = translate;
  window.zipBugLanguage = isBurmese ? 'my' : 'en';
  document.documentElement.lang = window.zipBugLanguage;
  if (!isBurmese) return;

  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const original = node.nodeValue;
    const trimmed = original.trim();
    const localized = translate(trimmed);
    if (localized !== trimmed) {
      const start = original.indexOf(trimmed);
      node.nodeValue = original.slice(0, start) + localized + original.slice(start + trimmed.length);
    }
  }

  document.querySelectorAll('[title], [aria-label], [placeholder]').forEach((element) => {
    ['title', 'aria-label', 'placeholder'].forEach((attribute) => {
      if (element.hasAttribute(attribute)) {
        element.setAttribute(attribute, translate(element.getAttribute(attribute)));
      }
    });
  });
})();
