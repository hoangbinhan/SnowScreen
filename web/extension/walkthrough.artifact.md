# Walkthrough

Created a standalone unpacked Chrome/Edge Manifest V3 extension in this directory. Weather preset values follow Android MainActivity, and Canvas physics follow SnowView's depth layers, sway, rotation, melting and randomized gusts. The pointer supplies optional directional wind in place of Android sensor tilt.

Validation: `node web/extension/tests/verify.cjs` passed. Covers manifest asset references, JavaScript syntax, settings validation, all eight crystal render branches, simulated frames, delta clamping, single animation loop, stop/restart, storage initialization race, tab visibility, reduced motion and page lifecycle. `git diff --check` passed.

No interactive browser is available in this session; actual Chrome/Edge loading and visual behavior require the manual checks in README.md. Android sources were not modified; a Gradle build is not applicable to these standalone browser files.

Update 1.0.1: replaced the snow checkbox with an accessible Start snow / Stop snow button; translated all popup text and manifest description to English; changed Gentle/default size to 0.4×; added the requested Buy Me a Coffee link beside the title; exported existing Android app_icon_512.png to four extension icon sizes for the popup, toolbar and extension listing. Existing saved preferences are preserved.

Re-ran automated verification successfully. Additional mocked popup checks passed for toggle state, persisted on/off values and reset to 0.4×; PNG dimensions match all manifest icon sizes. Interactive browser verification remains pending.

Follow-up: restored the original snowflake header icon and removed Android icon declarations/assets. Replaced the subtitle with a text donation link to the requested Buy Me a Coffee URL. Start snow now has a bright blue background and a repeating light sweep, disabled under reduced motion; Stop snow retains its distinct active state. Automated verification and whitespace checks passed after these changes.

Toolbar icon update: exported app/src/main/ic_launcher-playstore.png to 16/32 px PNG assets and set action.default_icon. Visually compared it against Android mipmap launcher resources: white snowflake on a blue gradient. The root app_icon_512.png is a different design and was replaced. The popup snowflake remains unchanged.
