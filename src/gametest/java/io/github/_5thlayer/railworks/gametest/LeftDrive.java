// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.railworks.gametest;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.railworks.rail.DriveSide;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.server.level.ServerLevel;

/**
 * A test environment that drives on the left, as a server with {@code driveSide = "LEFT"} does, and
 * puts the drive side back after. Vanilla runs the tests of one environment as a batch of their own,
 * so no test driving on the right runs beside them.
 */
record LeftDrive() implements TestEnvironmentDefinition<DriveSide> {

    static final MapCodec<LeftDrive> CODEC = MapCodec.unit(LeftDrive::new);

    @Override
    public DriveSide setup(ServerLevel level) {
        DriveSide before = DriveSide.current();
        DriveSide.configure(DriveSide.LEFT);
        return before;
    }

    @Override
    public void teardown(ServerLevel level, DriveSide before) {
        DriveSide.configure(before);
    }

    @Override
    public MapCodec<LeftDrive> codec() {
        return CODEC;
    }
}
