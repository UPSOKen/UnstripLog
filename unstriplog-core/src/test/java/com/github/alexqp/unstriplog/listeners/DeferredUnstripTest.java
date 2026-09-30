package com.github.alexqp.unstriplog.listeners;

import com.github.alexqp.commons.messages.ConsoleMessage;
import com.github.alexqp.unstriplog.main.InternalsProvider;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeferredUnstripTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Block block = mock(Block.class);
    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = mock(PlayerInventory.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final List<Runnable> nextTick = new ArrayList<>();
    private final Map<Integer, Runnable> expiryTasks = new HashMap<>();
    private final Map<String, MetadataValue> metadata = new HashMap<>();
    private final InternalsProvider internals = new InternalsProvider();
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<ConsoleMessage> debug;
    private BlockData current;
    private int taskId;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        debug = mockStatic(ConsoleMessage.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.IRON_AXE));
        when(player.isSneaking()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(block.getLocation()).thenReturn(new Location(null, 1, 2, 3));
        when(block.getType()).thenAnswer(call -> current.getMaterial());
        when(block.getBlockData()).thenAnswer(call -> current.clone());
        doAnswer(call -> { current = data(call.getArgument(0), Axis.Y); return null; }).when(block).setType(any(Material.class));
        doAnswer(call -> { current = ((BlockData) call.getArgument(0)).clone(); return null; }).when(block).setBlockData(any(BlockData.class));
        doAnswer(call -> { metadata.put(call.getArgument(0), call.getArgument(1)); return null; }).when(block).setMetadata(anyString(), any(MetadataValue.class));
        when(block.hasMetadata(anyString())).thenAnswer(call -> metadata.containsKey(call.getArgument(0)));
        when(block.getMetadata(anyString())).thenAnswer(call -> List.of(metadata.get(call.getArgument(0))));
        doAnswer(call -> { metadata.remove(call.getArgument(0)); return null; }).when(block).removeMetadata(anyString(), eq(plugin));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            nextTick.add(call.getArgument(1));
            return task(++taskId);
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
            int id = ++taskId;
            expiryTasks.put(id, call.getArgument(1));
            return task(id);
        });
        doAnswer(call -> { expiryTasks.remove(call.getArgument(0)); return null; }).when(scheduler).cancelTask(anyInt());
    }

    @AfterEach
    void closeMocks() {
        debug.close();
        bukkit.close();
    }

    @ParameterizedTest
    @EnumSource(Axis.class)
    void unchangedInfiniteLogRestoresOriginalAndPreservesAxis(Axis axis) {
        current = data(Material.STRIPPED_OAK_LOG, axis);
        log(-1, true, false).onInteract(click(Material.IRON_AXE));
        assertEquals(Material.STRIPPED_OAK_LOG, current.getMaterial(), "Undo remains deferred");
        runNextTick();
        assertEquals(Material.OAK_LOG, current.getMaterial());
        assertEquals(axis, ((Orientable) current).getAxis());
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"STONE", "AIR", "BIRCH_LOG", "STRIPPED_BIRCH_LOG"})
    void changedLogIsNeverMutated(Material replacement) {
        current = data(Material.STRIPPED_OAK_LOG, Axis.X);
        log(-1, true, false).onInteract(click(Material.IRON_AXE));
        current = data(replacement, Axis.Z);
        assertDoesNotThrow(this::runNextTick);
        assertEquals(replacement, current.getMaterial());
        verify(block, never()).setType(any(Material.class));
        verify(block, never()).setBlockData(any(BlockData.class));
    }

    @Test
    void sameLogTypeWithChangedAxisIsNotOverwritten() {
        current = data(Material.STRIPPED_OAK_LOG, Axis.X);
        log(-1, true, false).onInteract(click(Material.IRON_AXE));
        current = data(Material.STRIPPED_OAK_LOG, Axis.Z);
        runNextTick();
        assertEquals(Material.STRIPPED_OAK_LOG, current.getMaterial());
        assertEquals(Axis.Z, ((Orientable) current).getAxis());
        verify(block, never()).setType(any(Material.class));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"STONE", "AIR", "DIRT", "OAK_LOG"})
    void changedPathIsNeverMutated(Material replacement) {
        current = data(Material.DIRT_PATH, Axis.Y);
        grass(-1).onInteract(click(Material.IRON_SHOVEL));
        current = data(replacement, Axis.X);
        runNextTick();
        assertEquals(replacement, current.getMaterial());
        verify(block, never()).setType(any(Material.class));
    }

    @Test
    void unchangedInfinitePathStillBecomesDirt() {
        current = data(Material.DIRT_PATH, Axis.Y);
        grass(-1).onInteract(click(Material.IRON_SHOVEL));
        runNextTick();
        assertEquals(Material.DIRT, current.getMaterial());
    }

    @Test
    void timedPathRestoresRecordedOriginal() {
        GrassStripListener listener = grass(100);
        current = data(Material.PODZOL, Axis.Y);
        listener.onInteract(click(Material.IRON_SHOVEL));
        assertEquals(1, expiryTasks.size());
        current = data(Material.DIRT_PATH, Axis.Y);
        listener.onInteract(click(Material.IRON_SHOVEL));
        assertTrue(expiryTasks.isEmpty());
        runNextTick();
        assertEquals(Material.PODZOL, current.getMaterial());
    }

    @Test
    void timedLogRestoresRecordedOriginal() {
        LogStripListener listener = log(100, true, false);
        current = data(Material.BIRCH_WOOD, Axis.Z);
        listener.onInteract(click(Material.IRON_AXE));
        current = data(Material.STRIPPED_BIRCH_WOOD, Axis.Z);
        listener.onInteract(click(Material.IRON_AXE));
        runNextTick();
        assertEquals(Material.BIRCH_WOOD, current.getMaterial());
        assertEquals(Axis.Z, ((Orientable) current).getAxis());
    }

    @Test
    void replacementAlsoAbortsTimedUndoWithRecordedOriginal() {
        GrassStripListener listener = grass(100);
        current = data(Material.GRASS_BLOCK, Axis.Y);
        listener.onInteract(click(Material.IRON_SHOVEL));
        current = data(Material.DIRT_PATH, Axis.Y);
        listener.onInteract(click(Material.IRON_SHOVEL));
        current = data(Material.STONE, Axis.Y);
        runNextTick();
        assertEquals(Material.STONE, current.getMaterial());
        verify(block, never()).setType(any(Material.class));
    }

    @Test
    void expiredWindowStillPreventsUndo() {
        LogStripListener listener = log(100, true, false);
        current = data(Material.OAK_LOG, Axis.Y);
        listener.onInteract(click(Material.IRON_AXE));
        new ArrayList<>(expiryTasks.values()).forEach(Runnable::run);
        current = data(Material.STRIPPED_OAK_LOG, Axis.Y);
        listener.onInteract(click(Material.IRON_AXE));
        assertTrue(nextTick.isEmpty());
    }

    @Test
    void disabledWindowStillPreventsUndo() {
        current = data(Material.STRIPPED_OAK_LOG, Axis.Y);
        log(0, true, false).onInteract(click(Material.IRON_AXE));
        assertTrue(nextTick.isEmpty());
    }

    @Test
    void permissionStillRequired() {
        current = data(Material.STRIPPED_OAK_LOG, Axis.Y);
        when(player.hasPermission("unstriplog.wood")).thenReturn(false);
        log(-1, true, false).onInteract(click(Material.IRON_AXE));
        assertTrue(nextTick.isEmpty());
    }

    @Test
    void sneakingStillRequiredWhenConfigured() {
        current = data(Material.STRIPPED_OAK_LOG, Axis.Y);
        when(player.isSneaking()).thenReturn(false);
        log(-1, true, false).onInteract(click(Material.IRON_AXE));
        assertTrue(nextTick.isEmpty());
    }

    @Test
    void strippingSneakRequirementStillCancelsOriginalInteraction() {
        current = data(Material.OAK_LOG, Axis.Y);
        when(player.isSneaking()).thenReturn(false);
        PlayerInteractEvent event = click(Material.IRON_AXE);
        log(-1, true, true).onInteract(event);
        assertTrue(event.isCancelled());
        assertTrue(metadata.isEmpty());
    }

    @Test
    void mainHandPriorityStillIgnoresOffHandTool() {
        current = data(Material.STRIPPED_OAK_LOG, Axis.Y);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                new ItemStack(Material.IRON_AXE), block, BlockFace.UP, EquipmentSlot.OFF_HAND);
        log(-1, true, false).onInteract(event);
        assertTrue(nextTick.isEmpty());
    }

    @Test
    void wrongToolStillDoesNothing() {
        current = data(Material.STRIPPED_OAK_LOG, Axis.Y);
        log(-1, true, false).onInteract(click(Material.STICK));
        assertTrue(nextTick.isEmpty());
    }

    @Test
    void lateCancellationBehaviorIsIntentionallyUnchanged() {
        // The separate protection-cancellation change is explicitly outside this patch's scope.
        current = data(Material.STRIPPED_OAK_LOG, Axis.Y);
        PlayerInteractEvent event = click(Material.IRON_AXE);
        log(-1, true, false).onInteract(event);
        event.setCancelled(true);
        runNextTick();
        assertEquals(Material.OAK_LOG, current.getMaterial());
    }

    @Test
    void eventPriorityAndIgnoreCancelledRemainUnchanged() throws Exception {
        EventHandler handler = BlockStripListener.class.getMethod("onInteract", PlayerInteractEvent.class).getAnnotation(EventHandler.class);
        assertEquals(EventPriority.NORMAL, handler.priority());
        assertTrue(handler.ignoreCancelled());
    }

    private LogStripListener log(int delay, boolean sneakUndo, boolean sneakStrip) {
        return new LogStripListener(plugin, internals, delay, sneakUndo, sneakStrip);
    }

    private GrassStripListener grass(int delay) {
        return new GrassStripListener(plugin, internals, delay, true, false);
    }

    private PlayerInteractEvent click(Material tool) {
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(tool), block, BlockFace.UP, EquipmentSlot.HAND);
    }

    private void runNextTick() {
        assertEquals(1, nextTick.size(), "Exactly one deferred undo expected");
        nextTick.remove(0).run();
    }

    private BukkitTask task(int id) {
        BukkitTask task = mock(BukkitTask.class);
        when(task.getTaskId()).thenReturn(id);
        return task;
    }

    /** Stateful test double: full-state equality and independent clones, like Bukkit BlockData. */
    private static BlockData data(Material type, Axis initialAxis) {
        Axis[] axis = {initialAxis};
        boolean orientable = type.name().endsWith("_LOG") || type.name().endsWith("_WOOD");
        Class<?> api = orientable ? Orientable.class : BlockData.class;
        return (BlockData) Proxy.newProxyInstance(BlockData.class.getClassLoader(), new Class<?>[]{api}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getMaterial": return type;
                case "getAxis": return axis[0];
                case "setAxis": axis[0] = (Axis) args[0]; return null;
                case "getAxes": return EnumSet.allOf(Axis.class);
                case "clone": return data(type, axis[0]);
                case "getAsString":
                case "toString": return type.name() + (orientable ? "[axis=" + axis[0] + "]" : "");
                case "hashCode": return Objects.hash(type, orientable ? axis[0] : null);
                case "equals": return args[0] instanceof BlockData && ((BlockData) proxy).getAsString().equals(((BlockData) args[0]).getAsString());
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
    }
}
