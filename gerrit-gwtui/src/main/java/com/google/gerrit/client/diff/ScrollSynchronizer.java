// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.diff;

import com.google.gwt.user.client.Timer;

import net.codemirror.lib.CodeMirror;
import net.codemirror.lib.ScrollInfo;

class ScrollSynchronizer {
  private DiffTable diffTable;
  private LineMapper mapper;
  private OverviewBar overview;
  private ScrollCallback active;

  ScrollSynchronizer(DiffTable diffTable,
      CodeMirror cmA, CodeMirror cmB,
      LineMapper mapper) {
    this.diffTable = diffTable;
    this.mapper = mapper;
    this.overview = diffTable.overview;

    cmA.on("scroll", new ScrollCallback(cmA, cmB, DisplaySide.A));
    cmB.on("scroll", new ScrollCallback(cmB, cmA, DisplaySide.B));
  }

  private void updateScreenHeader(ScrollInfo si) {
    if (si.getTop() == 0 && !diffTable.isHeaderVisible()) {
      diffTable.setHeaderVisible(true);
    } else if (si.getTop() > 0.5 * si.getClientHeight()
        && diffTable.isHeaderVisible()) {
      diffTable.setHeaderVisible(false);
    }
  }

  class ScrollCallback implements Runnable {
    private final CodeMirror src;
    private final CodeMirror dst;
    private final DisplaySide srcSide;
    private final Timer fixup;
    private int state;

    ScrollCallback(CodeMirror src, CodeMirror dst, DisplaySide srcSide) {
      this.src = src;
      this.dst = dst;
      this.srcSide = srcSide;
      this.fixup = new Timer() {
        @Override
        public void run() {
          if (active == ScrollCallback.this) {
            fixup();
          }
        }
      };
    }

    @Override
    public void run() {
      if (active == null) {
        active = this;
        fixup.scheduleRepeating(20);
      }
      if (active == this) {
        ScrollInfo si = src.getScrollInfo();
        updateScreenHeader(si);
        overview.update(si);
        dst.scrollTo(si.getLeft(), align(si.getTop()));
        state = 0;
      }
    }

    private void fixup() {
      switch (state) {
        case 0:
          state = 1;
          dst.scrollToY(align(src.getScrollInfo().getTop()));
          break;
        case 1:
          state = 2;
          break;
        case 2:
          active = null;
          fixup.cancel();
          break;
      }
    }

    private double align(double srcTop) {
      // Since CM doesn't always take the height of line widgets into
      // account when calculating scrollInfo when scrolling too fast (e.g.
      // throw scrolling), simply setting scrollTop to be the same doesn't
      // guarantee alignment.

      int line = src.lineAtHeight(srcTop, "local");
      if (line == 0) {
        // Padding for insert at start of file occurs above line 0,
        // and CM3 doesn't always compute heightAtLine correctly.
        return srcTop;
      }

      // Find a pair of lines that are aligned and near the top of
      // the viewport. Use that distance to correct the Y coordinate.
      LineMapper.AlignedPair p = mapper.align(srcSide, line);
      double sy = src.heightAtLine(p.src, "local");
      double dy = dst.heightAtLine(p.dst, "local");
      return Math.max(0, dy + (srcTop - sy));
    }
  }
}
