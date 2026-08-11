package com.yeungzhy.yeed.common.core.support;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 树形结构处理工具类
 *
 * @author yeungzhy
 */
public class TreeUtil {
    private TreeUtil() {}


    /**
     * 构建树形结构（将扁平列表转换为树）
     *
     * @param flatList       需要转换的扁平数据列表
     * @param parentIdGetter 获取父节点 ID 的方法，如：TreeNode::getParentId
     * @param idGetter       获取当前节点 ID 的方法，如：TreeNode::getId
     * @param rootPredicate  判断是否为根节点的条件，如：node -> node.getParentId() == 0
     * @param childrenSetter 设置子节点列表的方法，如：TreeNode::setChildren
     * @param <T>            ID 字段的类型
     * @param <E>            节点实体类型
     *
     * @return 组装好的树形结构列表
     */
    public static <T, E> List<E> buildTree(List<E> flatList,
                                           Function<E, T> parentIdGetter,
                                           Function<E, T> idGetter,
                                           Predicate<E> rootPredicate,
                                           BiConsumer<E, List<E>> childrenSetter) {
        // 按父 ID 分组，使用 Optional 兼容父 ID 为 null 的情况
        // 使用 LinkedHashMap 保持插入顺序
        Map<Optional<T>, List<E>> parentMap = flatList.stream().collect(Collectors.groupingBy(
                node -> Optional.ofNullable(parentIdGetter.apply(node)),
                LinkedHashMap::new,
                Collectors.toList()
        ));

        List<E> result = new ArrayList<>();
        for (E node : flatList) {
            // 将当前节点作为父节点，找到它的所有子节点并设置进去
            List<E> children = parentMap.get(Optional.ofNullable(idGetter.apply(node)));
            childrenSetter.accept(node, children);

            // 如果是根节点，加入结果集
            if (rootPredicate.test(node)) {
                result.add(node);
            }
        }
        return result;
    }


    /**
     * 将树形结构打平成列表（默认使用后序遍历，并清理子节点引用）
     *
     * @param tree            树形结构列表
     * @param childrenGetter  获取子节点列表的方法，如：TreeNode::getChildren
     * @param childrenCleaner 清理子节点列表的方法（如置空以辅助 GC 或序列化），如：node -> node.setChildren(null)
     * @param <E>             节点实体类型
     * @return 打平后的节点列表
     *
     * <h3>实用场景：树的深拷贝</h3>
     * <p>
     * 由于打平后的节点已经清空了子节点引用（脱离了嵌套关系），此时配合 {@code BeanUtils} 等工具对单个节点进行浅拷贝是安全的。
     * 拷贝完成后，再使用 {@link #buildTree} 重新组装，即可完美实现一整棵树的深拷贝，且性能远高于 JSON 序列化方式。
     * </p>
     * <p>使用示例：</p>
     * <pre>{@code
     * // 1. 将树打平，并清空 children 引用
     * List<TreeNode> flatList = TreeUtil.flatten(tree, TreeNode::getChildren, m -> node -> node.setChildren(null));
     *
     * // 2. 拷贝扁平数据中的每个对象
     * List<TreeNode> copiedList = flatList.stream().map(src -> {
     *     TreeNode target = new TreeNode();
     *     BeanUtils.copyProperties(src, target);
     *     return target;
     * }).collect(Collectors.toList());
     *
     * // 3. 重新组装为新树
     * List<TreeNode> newTree = TreeUtil.buildTree(copiedList, TreeNode::getParentId, TreeNode::getId, node -> node.getParentId() == 0, TreeNode::setChildren);
     * }</pre>
     */
    public static <E> List<E> flatten(List<E> tree,
                                      Function<E, List<E>> childrenGetter,
                                      Consumer<E> childrenCleaner) {

        List<E> result = new ArrayList<>();
        forPostOrder(tree, item -> {
            childrenCleaner.accept(item); // 清理子节点引用
            result.add(item);
        }, childrenGetter);
        return result;
    }


    /**
     * 前序遍历（先处理父节点，再处理子节点）
     *
     * @param tree           树形结构列表
     * @param consumer       对节点的处理逻辑，如：node -> System.out.println(node)
     * @param childrenGetter 获取子节点列表的方法，如：TreeNode::getChildren
     * @param <E>            节点实体类型
     */
    public static <E> void forPreOrder(List<E> tree,
                                       Consumer<E> consumer,
                                       Function<E, List<E>> childrenGetter) {
        for (E node : tree) {
            consumer.accept(node);
            List<E> children = childrenGetter.apply(node);
            if (children != null && !children.isEmpty()) {
                forPreOrder(children, consumer, childrenGetter);
            }
        }
    }


    /**
     * 层序遍历（BFS，按层级从上到下，同层级从左到右）
     *
     * @param tree           树形结构列表
     * @param consumer       对节点的处理逻辑，如：node -> System.out.println(node)
     * @param childrenGetter 获取子节点列表的方法，如：TreeNode::getChildren
     * @param <E>            节点实体类型
     */
    public static <E> void forLevelOrder(List<E> tree,
                                         Consumer<E> consumer,
                                         Function<E, List<E>> childrenGetter) {

        Queue<E> queue = new LinkedList<>(tree);
        while (!queue.isEmpty()) {
            E node = queue.poll();
            consumer.accept(node);
            List<E> childList = childrenGetter.apply(node);
            if (childList != null && !childList.isEmpty()) {
                queue.addAll(childList);
            }
        }
    }


    /**
     * 后序遍历（先处理子节点，再处理父节点）
     *
     * @param tree           树形结构列表
     * @param consumer       对节点的处理逻辑，如：node -> System.out.println(node)
     * @param childrenGetter 获取子节点列表的方法，如：TreeNode::getChildren
     * @param <E>            节点实体类型
     */
    public static <E> void forPostOrder(List<E> tree,
                                        Consumer<E> consumer,
                                        Function<E, List<E>> childrenGetter) {
        for (E node : tree) {
            List<E> childList = childrenGetter.apply(node);
            if (childList != null && !childList.isEmpty()) {
                forPostOrder(childList, consumer, childrenGetter);
            }
            consumer.accept(node);
        }
    }


    /**
     * 对树的所有子节点进行排序
     *
     * @param tree           树形结构列表
     * @param comparator     排序规则，如：Comparator.comparing(TreeNode::getOrder)
     * @param childrenGetter 获取子节点列表的方法，如：TreeNode::getChildren
     * @param <E>            节点实体类型
     *
     * @return 排序后的树（原集合顺序会被改变）
     */
    public static <E> List<E> sort(List<E> tree,
                                   Comparator<? super E> comparator,
                                   Function<E, List<E>> childrenGetter) {
        for (E node : tree) {
            List<E> childList = childrenGetter.apply(node);
            if (childList != null && !childList.isEmpty()) {
                sort(childList, comparator, childrenGetter);
            }
        }
        tree.sort(comparator);
        return tree;
    }


    /**
     * 过滤树节点（保留命中节点及其子树，移除未命中节点及其子树）
     * <p><b>注意：此方法会直接修改传入的原集合，如果原数据需要保留，请先进行深拷贝！</b>
     *
     * @param tree           树形结构列表
     * @param predicate      过滤条件，如：node -> node.getStatus() == 1
     * @param childrenGetter 获取子节点列表的方法，如：TreeNode::getChildren
     * @param <E>            节点实体类型
     *
     * @return 过滤后的树
     */
    public static <E> List<E> filter(List<E> tree,
                                     Predicate<E> predicate,
                                     Function<E, List<E>> childrenGetter) {

        Iterator<E> iterator = tree.iterator();
        while (iterator.hasNext()) {
            E node = iterator.next();
            // 如果当前节点满足条件，保留并继续向下递归过滤
            if (predicate.test(node)) {
                List<E> childList = childrenGetter.apply(node);
                if (childList != null && !childList.isEmpty()) {
                    filter(childList, predicate, childrenGetter);
                }
            } else {
                // 不满足条件，直接移除（其子树也随之丢弃）
                iterator.remove();
            }
        }
        return tree;
    }


    /**
     * 搜索树节点（保留命中节点及其通往根节点的路径，剪除其他无关分支）
     * <p><b>注意：此方法会直接修改传入的原集合（使用迭代器 remove），如果原数据需要保留，请先进行深拷贝！</b>
     *
     * @param tree           树形结构列表
     * @param predicate      搜索条件，如：node -> "target".equals(node.getName())
     * @param childrenGetter 获取子节点列表的方法，如：TreeNode::getChildren
     * @param <E>            节点实体类型
     * @return 包含命中节点路径的树
     */
    public static <E> List<E> search(List<E> tree,
                                     Predicate<E> predicate,
                                     Function<E, List<E>> childrenGetter) {

        Iterator<E> iterator = tree.iterator();
        while (iterator.hasNext()) {
            E node = iterator.next();
            List<E> childList = childrenGetter.apply(node);
            // 先递归处理子节点
            if (childList != null && !childList.isEmpty()) {
                search(childList, predicate, childrenGetter);
            }
            // 如果当前节点不命中，且子节点为空（说明子节点也没命中或本来就没子节点），则移除
            // 如果子节点不为空，说明下方有命中项，即使当前节点不命中，也必须保留作为路径
            if (!predicate.test(node) && (childList == null || childList.isEmpty())) {
                iterator.remove();
            }
        }
        return tree;
    }

}
