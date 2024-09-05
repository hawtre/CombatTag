plugins {
    id("io.papermc.paperweight.userdev") version "1.7.2"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

dependencies {
    paperweight {
      paperDevBundle("1.21.1-R0.1-SNAPSHOT")
    }

    compileOnly("com.github.TownyAdvanced:towny:0.97.5.0")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.11-SNAPSHOT")
}
