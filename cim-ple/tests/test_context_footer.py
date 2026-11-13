from __future__ import annotations

import unittest
from pathlib import Path

from textual.widgets import Tree

from cim_ple.UI.cim_browser import CimBrowser

SPEC_DIRECTORY = Path(__file__).resolve().parents[2] / "spec"


def footer_text(app: CimBrowser) -> str:
    return app._context_footer.hint_text


async def settle(pilot) -> None:
    for _ in range(3):
        await pilot.pause()


class ContextFooterTests(unittest.IsolatedAsyncioTestCase):
    async def test_footer_shows_the_keys_of_the_current_context(self) -> None:
        app = CimBrowser(SPEC_DIRECTORY)
        async with app.run_test(size=(160, 40)) as pilot:
            await settle(pilot)
            self.assertIn("open class", footer_text(app))  # search input focused on start

            app.query_one("#tree", Tree).focus()
            await settle(pilot)
            self.assertIn("collapse/expand", footer_text(app))  # navigation tree keys

            app.query_one("#saved-class-tree", Tree).focus()
            await settle(pilot)
            self.assertIn("remove", footer_text(app))  # saved class keys
            self.assertNotIn("open class", footer_text(app))

            await pilot.press("c")
            await settle(pilot)
            self.assertIn("compare", footer_text(app))  # commit prompt keys

            await pilot.press("enter")  # empty input defaults to main, opens the diff panel
            await settle(pilot)
            self.assertIn("close panel", footer_text(app))

            await pilot.press("q")
            await settle(pilot)
            self.assertIn("remove", footer_text(app))  # back to the saved class tree

    async def test_footer_shows_generic_keys_without_focus(self) -> None:
        app = CimBrowser(SPEC_DIRECTORY)
        async with app.run_test(size=(160, 40)) as pilot:
            await settle(pilot)
            await pilot.press("escape")  # the app's own "clear search" un-focuses the input
            await settle(pilot)
            self.assertIn("diff spec", footer_text(app))
            self.assertNotIn("open class", footer_text(app))


if __name__ == "__main__":
    unittest.main()
