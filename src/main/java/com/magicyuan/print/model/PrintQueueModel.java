package com.magicyuan.print.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.*;

import com.magicyuan.print.dto.PrintDTO;

/**
 * 打印队列数据模型
 * 管理打印任务的数据状态
 *
 * @author Magic_yuan
 * @version 2.0.0
 */
public class PrintQueueModel extends AbstractListModel<PrintDTO> {

    private final List<PrintDTO> printQueue;

    private final List<PrintDTO> errorQueue;

    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    /** 任务列表记录上限(超过即清空整个列表,默认100) */
    private int maxTaskCount = 100;

    public PrintQueueModel() {
        this.printQueue = new CopyOnWriteArrayList<>();
        this.errorQueue = new CopyOnWriteArrayList<>();
    }

    @Override
    public int getSize() {
        return printQueue.size();
    }

    @Override
    public PrintDTO getElementAt(int index) {
        return printQueue.get(index);
    }

    /**
     * 添加打印任务
     */
    public void addPrintTask(PrintDTO printDTO) {
        if (printDTO.getCreateTime() == 0) {
            printDTO.setCreateTime(System.currentTimeMillis());
        }
        printQueue.add(printDTO);
        int index = printQueue.size() - 1;
        fireIntervalAdded(this, index, index);
        enforceLimit();
        fireChanged();
    }

    /**
     * 批量添加打印任务
     */
    public void addPrintTasks(List<PrintDTO> printDTOs) {
        if (printDTOs == null || printDTOs.isEmpty()) {
            return;
        }

        int startIndex = printQueue.size();
        printQueue.addAll(printDTOs);
        int endIndex = printQueue.size() - 1;
        fireIntervalAdded(this, startIndex, endIndex);
        fireChanged();
    }

    /**
     * 移除打印任务
     */
    public void removePrintTask(int index) {
        if (index >= 0 && index < printQueue.size()) {
            printQueue.remove(index);
            fireIntervalRemoved(this, index, index);
            fireChanged();
        }
    }

    /**
     * 移除所有打印任务
     */
    public void clearPrintQueue() {
        int size = printQueue.size();
        if (size > 0) {
            printQueue.clear();
            fireIntervalRemoved(this, 0, size - 1);
            fireChanged();
        }
    }

    /**
     * 获取所有打印任务
     */
    public List<PrintDTO> getAllPrintTasks() {
        return new ArrayList<>(printQueue);
    }

    /**
     * 获取选中的打印任务
     */
    public List<PrintDTO> getSelectedPrintTasks(int[] selectedIndices) {
        List<PrintDTO> selectedTasks = new ArrayList<>();
        for (int index : selectedIndices) {
            if (index >= 0 && index < printQueue.size()) {
                selectedTasks.add(printQueue.get(index));
            }
        }
        return selectedTasks;
    }

    /**
     * 添加错误任务
     */
    public void addErrorTask(PrintDTO printDTO) {
        if (printDTO.getCreateTime() == 0) {
            printDTO.setCreateTime(System.currentTimeMillis());
        }
        errorQueue.add(printDTO);
        enforceLimit();
        fireChanged();
    }

    /**
     * 获取错误任务列表
     */
    public List<PrintDTO> getErrorTasks() {
        return new ArrayList<>(errorQueue);
    }

    /**
     * 清空错误任务
     */
    public void clearErrorQueue() {
        errorQueue.clear();
        fireChanged();
    }

    /**
     * 注册队列变更监听器(任务新增/移除/清空时触发,用于界面自动刷新)。
     */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    /**
     * 设置任务列表记录上限(<=0 时回退为默认100)。
     */
    public void setMaxTaskCount(int maxTaskCount) {
        this.maxTaskCount = maxTaskCount > 0 ? maxTaskCount : 100;
    }

    /**
     * 列表记录数超过上限时清空整个列表(避免无限增长)。
     */
    private void enforceLimit() {
        if (printQueue.size() + errorQueue.size() > maxTaskCount) {
            printQueue.clear();
            errorQueue.clear();
        }
    }

    /**
     * 通知所有变更监听器(打印队列或错误队列发生变化)。
     */
    private void fireChanged() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    /**
     * 检查队列是否为空
     */
    public boolean isEmpty() {
        return printQueue.isEmpty();
    }

    /**
     * 获取队列大小
     */
    public int getQueueSize() {
        return printQueue.size();
    }
}
